 Employee part (Java RMI, NetBeans / Maven) 

Demo accounts (seed data in `DataStore`, remove when HR registration works):
`E001/1234`, `E002/1234` (employees), `H001/hr1234` (HR), `A001/admin1234` (admin).

## Project structure

```
hrm.common   shared by EVERY part: Role, Employee, FamilyMember, LeaveApplication, LeaveStatus,
             ServiceResult, ServiceException, SessionExpiredException,
             AuthService (remote), EmployeeService (remote)
hrm.server   DataStore (central data, thread-safe), SessionManager (token -> user/role),
             PasswordUtil (PBKDF2 hashing), AuthServiceImpl, EmployeeServiceImpl, HRMServer (main)
hrm.client   EmployeeClient (console menu), ConcurrencyDemo (test tool)
```

Employee features: login, view/update profile, update family details, check leave balance,
apply for leave, check leave status.

## How to integrate HR / Admin

1. **Remote interface** in `hrm.common`, e.g. `HRService extends Remote` with `String BINDING_NAME = "HRService";`
   Every method takes `String token` first and declares `throws RemoteException, ServiceException`.
2. **Implementation** in `hrm.server`, `extends UnicastRemoteObject`, with the same constructor as
   `EmployeeServiceImpl`: `(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf)` calling `super(port, csf, ssf)`.
   (This is what makes the SSL switch work for your service too.)
3. **Check the role first** in every HR/Admin method:
   `String callerId = SessionManager.getInstance().requireRole(token, Role.HR, Role.ADMIN);`
   (Use `Role.ADMIN` only for admin-only methods.) Do not accept an employee ID or role from the client to identify the caller.
4. **Use the DataStore hooks** (no need for your own storage):
   - `addEmployee(Employee, plainPassword)` register employee (password is hashed automatically)
   - `removeEmployee(id)` delete employee (their session stops working; leave history is kept for reports)
   - `getAllEmployees()`, `getEmployeeCopy(id)`
   - `getAllLeaves()`, `getLeavesFor(id)` for the yearly report
   - `decideLeave(leaveId, LeaveStatus.APPROVED/REJECTED)` approve or reject; approving deducts the balance atomically
5. **Publish it** in `HRMServer` at the `INTEGRATION POINT` comment:
   `registry.rebind(HRService.BINDING_NAME, new HRServiceImpl(0, csf, ssf));`
6. HR/Admin clients use the same `AuthService.login(...)`, then pass the token to their services.

Rules to keep everything compatible: do not change existing method signatures in `hrm.common`
without telling the others; add new methods/classes instead. Keep any new shared-data code inside
`DataStore` (synchronized) so all services see the same data.

##  Secure communication (SSL/TLS)

Create a keystore once (keytool is in your JDK's `bin` folder):

```
keytool -genkeypair -alias hrm -keyalg RSA -keysize 2048 -validity 365 -dname "CN=localhost" ^
        -ext SAN=dns:localhost,ip:127.0.0.1 -keystore hrm.keystore -storepass changeit -keypass changeit
```

NetBeans > right-click project > Properties > Run > **VM Options**:

- Server: `-Dhrm.ssl=true -Djavax.net.ssl.keyStore=C:/path/hrm.keystore -Djavax.net.ssl.keyStorePassword=changeit`
- Client: `-Dhrm.ssl=true -Djavax.net.ssl.trustStore=C:/path/hrm.keystore -Djavax.net.ssl.trustStorePassword=changeit`

(Set VM options per run configuration, or use one config for the server and another for the client.)
Without `-Dhrm.ssl=true` everything runs as plain RMI for easy debugging.
Clients on another computer also need: server `-Djava.rmi.server.hostname=<server IP>` and client `-Dhrm.host=<server IP>`.
Tested: a client with the right truststore connects; a client without it, and a plain client, are refused.

##  Design notes (useful for the report)

- **Multithreading:** RMI serves each call on its own thread. `DataStore` methods are `synchronized`;
  `applyLeave` checks balance + overlap and saves in one atomic step. Password hashing is done
  outside the lock so slow logins never block other users. `SessionManager` and the login-failure
  maps use `ConcurrentHashMap`. `ConcurrencyDemo` proves it: 10 simultaneous 5-day requests with a
  14-day balance gives exactly 2 accepted, 8 rejected.
- **Serialization:** `Employee`, `FamilyMember`, `LeaveApplication`, `ServiceResult` and the exceptions are
  `Serializable` (passed by value, with `serialVersionUID`). `AuthService` / `EmployeeService` are
  `Remote` (passed by reference as stubs). Passwords never travel inside `Employee`; the server keeps
  only salted PBKDF2 hashes. The server rebuilds family and leave objects from validated values
  instead of trusting what the client sent.
- **Security:** SSL/TLS option, session tokens with 30-minute idle timeout, role check on the server,
  lock-out for 1 minute after 5 wrong passwords, same error message for unknown ID and wrong password,
  server-side input validation.
- **Fault tolerance:** the client retries the connection 3 times, reconnects after a connection drop,
  and asks the user to log in again if the server restarted (sessions are in memory).
- **Maintainability / heterogeneity:** `DataStore` hides storage (swap in JDBC/file without changing
  services); Java-only serialization limits non-Java clients (JSON or Protocol Buffers would fix this).

##  Assumptions and limits

- Data is in memory and lost when the server stops (replace `DataStore` internals with a database to persist).
- All leave types deduct from one balance (default 14 days). Days are calendar days.
- Balance is deducted when HR **approves**; pending requests already count against what is available.
- New applications must start today or later and cannot overlap an existing pending/approved leave.

##  Test checklist

Login (right / wrong password / 5 wrong in a row = lock-out), view profile, update profile (valid / empty /
duplicate IC), family add/remove/save/cancel (valid / invalid name), balance, apply leave (valid / overlap /
over balance / end before start / past date / bad date format), leave status, server stopped while the client
is open (reconnect), server restarted (re-login), SSL on/off, `ConcurrencyDemo`.
