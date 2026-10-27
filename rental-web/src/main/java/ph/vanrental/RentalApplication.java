package ph.vanrental;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
@RestController
@RequestMapping("/api")
@CrossOrigin
public class RentalApplication {
  public static void main(String[] args) { SpringApplication.run(RentalApplication.class, args); }

  @Bean CommandLineRunner seed(JdbcTemplate db) { return args -> {
    Integer count = db.queryForObject("SELECT COUNT(*) FROM vans", Integer.class);
    if (count == null || count == 0) {
      Object[][] vans = {
        {"ABC-123","Toyota HiAce Commuter",15,"Standard",3000.0}, {"DEF-456","Nissan Urvan",15,"Standard",3000.0},
        {"GHI-789","Toyota HiAce Grandia",10,"Luxury",4500.0}, {"DEF-000","Kaicene",12,"Luxury",4500.0},
        {"JKL-201","Toyota HiAce Commuter",15,"Standard",3000.0}, {"MNO-202","Nissan Urvan NV350",15,"Standard",3000.0},
        {"PQR-203","Foton View Traveller",15,"Standard",3000.0}, {"STU-204","Hyundai H350",15,"Standard",3000.0},
        {"VWX-205","Toyota HiAce Commuter",15,"Standard",3000.0}, {"YZA-206","Nissan Urvan NV350",15,"Standard",3000.0}
      };
      for (Object[] v : vans) db.update("INSERT INTO vans(plate,model,seats,van_type,daily_rate,status) VALUES(?,?,?,?,?,'Available')",v);
    }
  }; }

  @GetMapping("/summary") Map<String,Object> summary() {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("vans", one("SELECT COUNT(*) FROM vans")); m.put("available", one("SELECT COUNT(*) FROM vans WHERE status='Available'"));
    m.put("activeRentals", one("SELECT COUNT(*) FROM rentals WHERE status='Active'")); m.put("customers", one("SELECT COUNT(*) FROM customers"));
    m.put("income", db.queryForObject("SELECT COALESCE(SUM(fee+late_fee),0) FROM rentals WHERE status='Returned'", BigDecimal.class)); return m;
  }
  private final JdbcTemplate db;
  public RentalApplication(JdbcTemplate db) { this.db=db; }
  private int one(String sql) { Integer n=db.queryForObject(sql,Integer.class); return n==null?0:n; }

  @GetMapping("/vans") List<Map<String,Object>> vans() { return db.queryForList("SELECT * FROM vans ORDER BY plate"); }
  @PostMapping("/vans") Map<String,Object> addVan(@RequestBody Map<String,Object> b) {
    String plate=required(b,"plate").toUpperCase(), model=required(b,"model"); int seats=integer(b,"seats"); double rate=number(b,"dailyRate");
    if(seats<1||rate<=0) throw bad("Seats and daily rate must be greater than zero.");
    try { db.update("INSERT INTO vans(plate,model,seats,van_type,daily_rate,status) VALUES(?,?,?,?,?,'Available')",plate,model,seats,Objects.toString(b.getOrDefault("type","Standard")),rate); }
    catch(Exception e){throw bad("Plate number already exists or the van details are invalid.");} return Map.of("message","Van added.");
  }
  @PutMapping("/vans/{plate}/maintenance") Map<String,Object> maintenance(@PathVariable String plate,@RequestBody Map<String,Object> b) {
    Map<String,Object> v=van(plate); String status=Objects.toString(b.get("status"),"");
    if("Under maintenance".equals(status)) { if("Rented".equals(v.get("STATUS"))) throw bad("A rented van cannot be sent to maintenance."); db.update("UPDATE vans SET status='Under maintenance' WHERE plate=?",plate); }
    else if("Available".equals(status)) db.update("UPDATE vans SET status='Available' WHERE plate=? AND status='Under maintenance'",plate);
    else throw bad("Unsupported status."); return Map.of("message","Van status updated.");
  }
  private Map<String,Object> van(String plate){ List<Map<String,Object>> r=db.queryForList("SELECT * FROM vans WHERE plate=?",plate); if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Van not found."); return r.get(0); }

  @GetMapping("/customers") List<Map<String,Object>> customers(){return db.queryForList("SELECT * FROM customers ORDER BY id DESC");}
  @PostMapping("/customers") Map<String,Object> addCustomer(@RequestBody Map<String,Object>b){
    String name=required(b,"name"); db.update("INSERT INTO customers(name,contact,email,address,id_type,id_number) VALUES(?,?,?,?,?,?)",name,b.get("contact"),b.get("email"),b.get("address"),b.get("idType"),b.get("idNumber")); return Map.of("message","Customer registered.");
  }
  @DeleteMapping("/customers/{id}") Map<String,Object> deleteCustomer(@PathVariable long id){if(one("SELECT COUNT(*) FROM rentals WHERE customer_id="+id)>0)throw bad("Customers with rental history cannot be deleted."); int n=db.update("DELETE FROM customers WHERE id=?",id); if(n==0)throw bad("Customer not found."); return Map.of("message","Customer deleted.");}

  @GetMapping("/rentals") List<Map<String,Object>> rentals(){return db.queryForList("SELECT r.*, c.name AS customer_name, v.model AS van_model FROM rentals r JOIN customers c ON c.id=r.customer_id JOIN vans v ON v.plate=r.plate ORDER BY r.id DESC");}
  @PostMapping("/rentals") Map<String,Object> rent(@RequestBody Map<String,Object>b){
    long cid=Long.parseLong(required(b,"customerId")); String plate=required(b,"plate"); LocalDate start=LocalDate.parse(required(b,"startDate")), end=LocalDate.parse(required(b,"endDate"));
    if(end.isBefore(start)) throw bad("End date cannot be before start date."); if(start.isBefore(LocalDate.now())) throw bad("Start date cannot be in the past.");
    if(one("SELECT COUNT(*) FROM customers WHERE id="+cid)==0)throw bad("Select a valid customer."); Map<String,Object> v=van(plate);
    if(!"Available".equals(v.get("STATUS")))throw bad("This van is not available for rent.");
    int days=(int)ChronoUnit.DAYS.between(start,end)+1; BigDecimal rate=new BigDecimal(v.get("DAILY_RATE").toString()); BigDecimal fee=rate.multiply(BigDecimal.valueOf(days));
    db.update("INSERT INTO rentals(customer_id,plate,start_date,end_date,days,daily_rate,fee,status,staff) VALUES(?,?,?,?,?,?,?,'Active',?)",cid,plate,start,end,days,rate,fee,Objects.toString(b.getOrDefault("staff","Rental Staff")));
    db.update("UPDATE vans SET status='Rented' WHERE plate=?",plate); return Map.of("message","Rental created.","days",days,"fee",fee);
  }
  @PostMapping("/rentals/{id}/return") Map<String,Object> returnVan(@PathVariable long id,@RequestBody(required=false) Map<String,Object>b){
    List<Map<String,Object>> rows=db.queryForList("SELECT * FROM rentals WHERE id=?",id); if(rows.isEmpty())throw bad("Rental not found."); Map<String,Object> r=rows.get(0); if(!"Active".equals(r.get("STATUS")))throw bad("This rental has already been returned.");
    LocalDate today=LocalDate.now(); LocalDate due=LocalDate.parse(r.get("END_DATE").toString()); long late=Math.max(0,ChronoUnit.DAYS.between(due,today)); BigDecimal lateFee=BigDecimal.valueOf(late*250L);
    db.update("UPDATE rentals SET status='Returned',return_date=?,late_fee=? WHERE id=?",today,lateFee,id);
    db.update("UPDATE vans SET status='Available' WHERE plate=? AND status='Rented'",r.get("PLATE")); return Map.of("message","Van returned.","lateDays",late,"lateFee",lateFee);
  }
  @GetMapping("/maintenance") List<Map<String,Object>> maintenanceList(){return db.queryForList("SELECT * FROM maintenance ORDER BY id DESC");}
  @PostMapping("/maintenance") Map<String,Object> addMaintenance(@RequestBody Map<String,Object>b){String plate=required(b,"plate"); Map<String,Object> v=van(plate); if("Rented".equals(v.get("STATUS")))throw bad("A rented van cannot be sent to maintenance."); String details=Objects.toString(b.getOrDefault("details","Routine service")); db.update("INSERT INTO maintenance(plate,details,service_date,staff,status) VALUES(?,?,?,?, 'Open')",plate,details,LocalDate.now(),Objects.toString(b.getOrDefault("staff","Rental Staff"))); db.update("UPDATE vans SET status='Under maintenance' WHERE plate=?",plate); return Map.of("message","Maintenance record created.");}
  private String required(Map<String,Object>b,String k){String s=Objects.toString(b.get(k),"").trim();if(s.isEmpty())throw bad("Please provide "+k+".");return s;}
  private int integer(Map<String,Object>b,String k){try{return Integer.parseInt(required(b,k));}catch(Exception e){throw bad("Invalid "+k+".");}}
  private double number(Map<String,Object>b,String k){try{return Double.parseDouble(required(b,k));}catch(Exception e){throw bad("Invalid "+k+".");}}
  private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  @ExceptionHandler(ResponseStatusException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) Map<String,String> handle(ResponseStatusException e){return Map.of("error",e.getReason()==null?"Request failed.":e.getReason());}
}
