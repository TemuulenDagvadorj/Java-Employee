package hrm.server;

import hrm.common.Employee;
import hrm.common.Role;
import hrm.common.ServiceException;
import hrm.common.SessionExpiredException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of logged-in users. Login hands out a random token; every later
 * call carries that token, so the server always knows WHO is calling and which
 * ROLE they have. Shared by all services (employee, HR, admin).
 *
 * HR/Admin services should start every method with:
 *     String id = SessionManager.getInstance().requireRole(token, Role.HR, Role.ADMIN);
 */
public final class SessionManager {
    private static final long TIMEOUT_MS = 30L * 60 * 1000; // 30 minutes idle

    private static final SessionManager INSTANCE = new SessionManager();

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    private static final class Session {
        final String employeeId;
        volatile long lastAccess = System.currentTimeMillis();

        Session(String employeeId) {
            this.employeeId = employeeId;
        }
    }

    // ConcurrentHashMap: many login/logout/calls happen at the same time on different RMI threads
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private SessionManager() { }

    public String createSession(String employeeId) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(employeeId));
        return token;
    }

    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }

    /** Returns the employee ID behind a valid token, otherwise throws SessionExpiredException. */
    public String requireEmployeeId(String token) throws SessionExpiredException {
        Session s = token == null ? null : sessions.get(token);
        long now = System.currentTimeMillis();
        if (s == null || now - s.lastAccess > TIMEOUT_MS || !DataStore.getInstance().exists(s.employeeId)) {
            invalidate(token);
            throw new SessionExpiredException();
        }
        s.lastAccess = now;
        return s.employeeId;
    }

    /** Like requireEmployeeId, but also checks the caller has one of the allowed roles. */
    public String requireRole(String token, Role... allowed) throws ServiceException {
        String id = requireEmployeeId(token);
        Employee e = DataStore.getInstance().getEmployeeCopy(id);
        if (e == null) throw new SessionExpiredException();
        for (Role r : allowed) {
            if (e.getRole() == r) return id;
        }
        throw new ServiceException("Access denied: your role (" + e.getRole() + ") is not allowed to do this.");
    }

    public Role getRole(String token) throws ServiceException {
        String id = requireEmployeeId(token);
        Employee e = DataStore.getInstance().getEmployeeCopy(id);
        if (e == null) throw new SessionExpiredException();
        return e.getRole();
    }
}
