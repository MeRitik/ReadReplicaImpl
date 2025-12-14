# Read Replica Implementation in Spring Boot

A complete Spring Boot application demonstrating **read-write splitting** using a primary MySQL database and a read replica, with **native MySQL asynchronous replication** set up via Docker.

This project routes:
- Write operations (POST, PUT, DELETE) → Primary database
- Read operations (GET) → Replica database

Routing is transparent and based on `@Transactional(readOnly = true)` using `AbstractRoutingDataSource` and `LazyConnectionDataSourceProxy`.

## Features
- Spring Boot 3.x + Spring Data JPA
- Transaction-aware routing to primary/replica
- MySQL 8.0 primary-replica replication using Docker Compose
- REST API for CRUD operations on `Product` entity

## Project Structure
```
read_replica_impl/
├── src/main/java/com/ritik/read_replica_impl/
│   ├── config/
│   │   ├── DataSourceConfig.java
│   │   ├── DataSourceType.java
│   │   └── ReplicationRoutingDataSource.java
│   ├── controller/
│   │   └── ProductController.java
│   ├── entity/
│   │   └── Product.java
│   ├── repository/
│   │   └── ProductRepository.java
│   ├── service/
│   │   ├── ProductService.java
│   │   └── impl/IProductService.java
│   └── ReadReplicaImplApplication.java
├── docker-compose.yaml
├── application.yaml
└── pom.xml
```

## Prerequisites
- Java 21
- Maven
- Docker & Docker Compose

## Setup MySQL Primary-Replica with Docker

### 1. Start the Containers
Place this `docker-compose.yaml` in the project root:

```yaml
services:
  mysql-primary:
    image: mysql:8.0.36
    command: --server-id=1 --log-bin=mysql-bin --binlog-format=ROW
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: products
    ports:
      - "3307:3306"
    volumes:
      - primary-data:/var/lib/mysql

  mysql-replica:
    image: mysql:8.0.36
    command: --server-id=2
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_ROOT_HOST: '%'
    ports:
      - "3308:3306"
    volumes:
      - replica-data:/var/lib/mysql
    depends_on:
      - mysql-primary

volumes:
  primary-data:
  replica-data:
```

Run:
```bash
docker-compose up -d
```

### 2. Configure Replication (One-Time Setup)

#### On Primary (port 3307)
```sql
mysql -h 127.0.0.1 -P 3307 -u root -p
-- Password: password

CREATE USER 'repl'@'%' IDENTIFIED WITH 'mysql_native_password' BY 'replpass';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
SHOW MASTER STATUS;
-- Note the File (e.g., mysql-bin.000003) and Position (e.g., 849)
```

#### On Replica (port 3308)
```sql
mysql -h 127.0.0.1 -P 3308 -u root -p
-- Password: password

STOP SLAVE;

CHANGE MASTER TO
  MASTER_HOST='mysql-primary',
  MASTER_USER='repl',
  MASTER_PASSWORD='replpass',
  MASTER_LOG_FILE='mysql-bin.000003',   -- Use value from SHOW MASTER STATUS
  MASTER_LOG_POS=849;                   -- Use value from SHOW MASTER STATUS

START SLAVE;
SHOW SLAVE STATUS\G
```

If you encounter errors (e.g., duplicate user or failed transaction):
```sql
STOP SLAVE;
SET GLOBAL sql_slave_skip_counter = 1;
START SLAVE;
SHOW SLAVE STATUS\G
```

Success looks like:
```
Slave_IO_Running: Yes
Slave_SQL_Running: Yes
Seconds_Behind_Master: 0
```

## Spring Boot Configuration

### application.yaml
```yaml
spring:
  application:
    name: "read_replica_impl"

  datasource:
    primary:
      jdbc-url: jdbc:mysql://localhost:3307/products
      username: root
      password: password
      hikari:
        pool-name: PrimaryPool

    replica:
      jdbc-url: jdbc:mysql://localhost:3308/products
      username: root
      password: password
      hikari:
        pool-name: ReplicaPool

  jpa:
    hibernate:
      ddl-auto: validate   # or 'none' in production
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

## Key Implementation Details

### Routing Logic
- `ReplicationRoutingDataSource` extends `AbstractRoutingDataSource`
- Routes based on transaction read-only status
- Uses `LazyConnectionDataSourceProxy` to delay connection acquisition until query time

### Product Entity
```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal price;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // getters and setters
}
```

### Service Layer
- Write methods: `@Transactional`
- Read methods: `@Transactional(readOnly = true)` → routes to replica

## API Endpoints

| Method | Endpoint          | Description              |
|--------|-------------------|--------------------------|
| POST   | `/products`       | Create product           |
| GET    | `/products/{id}`  | Get product by ID        |
| GET    | `/products`       | Get all products         |
| PUT    | `/products/{id}`  | Update product           |
| DELETE | `/products/{id}`  | Delete product           |

## Testing the Setup

1. Start Docker containers: `docker-compose up -d`
2. Set up replication (commands above)
3. Run Spring Boot app: `mvn spring-boot:run`
4. Create a product:
   ```bash
   curl -X POST http://localhost:8080/products \
     -H "Content-Type: application/json" \
     -d '{"name":"Laptop","description":"High-end","price":999.99}'
   ```
5. Read products:
   ```bash
   curl http://localhost:8080/products
   ```

Check logs:
- Writes → `PrimaryPool`
- Reads → `ReplicaPool`

## Troubleshooting Tips
- Replication lag: Check `Seconds_Behind_Master`
- Connection refused: Ensure ports 3307/3308 are correct
- Authentication errors: Use `mysql_native_password` for replication user
- Schema issues: Use `@Column(name = "...")` for explicit mapping

## Production Recommendations
- Use GTID replication for easier management
- Monitor replication lag
- Use Flyway/Liquibase for schema migrations
- Secure credentials with environment variables/secrets
- Add health checks for both databases

**Enjoy your scalable read-replica setup!** 🚀

Project by Ritik — December 2025