# Vanly — Passenger Van Rental Management System

A web-based version of the CSC104 Passenger Van Rental System. The browser interface uses HTML, CSS, and JavaScript; the backend uses Java 17 and Spring Boot. It uses H2 locally and PostgreSQL on Render.

## Run locally

Requirements: Java 17+ and Maven 3.8+.

1. Open a terminal in the folder containing `pom.xml`.
2. Run `mvn spring-boot:run`.
3. Visit `http://localhost:8080`.

Local database default: `./data/vanrental`. The local H2 console is at `http://localhost:8080/h2-console`.

## Deploy to Render with PostgreSQL

This repository includes a `Dockerfile` and `render.yaml` for a Render Blueprint deployment.

1. Upload the contents of this folder to the root of a GitHub repository. Make sure `pom.xml`, `Dockerfile`, and `render.yaml` are at the repository root.
2. In Render, choose **New → Blueprint**, connect the GitHub repository, and apply the Blueprint.
3. Render will create a web service and a PostgreSQL database using the settings in `render.yaml`.
4. Wait for the build and deployment to finish, then open the `onrender.com` URL from the service page.

## Free-plan warning

Render's free PostgreSQL database expires after 30 days and has no backups. Export your database before expiration or upgrade it if you need to keep records. Free web services may sleep after inactivity. This is suitable for a school demonstration, not sensitive or production customer data.

## Included features

- Dashboard metrics and recent rentals
- Fleet listing, adding vans, daily rates, and status updates
- Customer registration and safe deletion rules for customers with rental history
- Rental bookings with inclusive day calculation and fee estimate
- Return processing and PHP 250 per day late fee after the end date
- Maintenance logs and rental-history views
- Responsive interface

## Notes

- This classroom starter has no login/authentication. Do not store real customer information publicly without adding authentication, authorization, stronger validation, and backups.
- Email and SMS delivery are not connected.
- Initial sample fleet includes Standard vans at PHP 3,000/day and Luxury vans at PHP 4,500/day. Confirm the Luxury rate against your instructor's original Java rules if needed.
