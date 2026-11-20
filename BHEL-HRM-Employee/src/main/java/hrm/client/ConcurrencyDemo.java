package hrm.client;

import hrm.common.AuthService;
import hrm.common.EmployeeService;
import hrm.common.LeaveApplication;
import hrm.common.ServiceResult;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


public class ConcurrencyDemo {
    public static void main(String[] args) throws Exception {
        String host = System.getProperty("hrm.host", "localhost");
        int port = Integer.getInteger("hrm.port", 1099);
        boolean ssl = Boolean.getBoolean("hrm.ssl");
        Registry registry = ssl ? LocateRegistry.getRegistry(host, port, new SslRMIClientSocketFactory())
                                : LocateRegistry.getRegistry(host, port);
        final AuthService auth = (AuthService) registry.lookup(AuthService.BINDING_NAME);
        final EmployeeService emp = (EmployeeService) registry.lookup(EmployeeService.BINDING_NAME);

        final int clients = 10;
        final CountDownLatch ready = new CountDownLatch(clients);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(clients);
        final AtomicInteger accepted = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < clients; i++) {
            final int n = i;
            new Thread(() -> {
                try {
                    String token = auth.login("E001", "1234");
                    LocalDate start = LocalDate.now().plusDays(10 + n * 10L); 
                    LeaveApplication app = new LeaveApplication("Annual", start, start.plusDays(4), "concurrency test");
                    ready.countDown();
                    go.await();                                               
                    ServiceResult r = emp.applyLeave(token, app);
                    (r.isSuccess() ? accepted : rejected).incrementAndGet();
                    System.out.println("Thread " + n + ": " + r);
                } catch (Exception e) {
                    System.out.println("Thread " + n + " error: " + e);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        ready.await();
        go.countDown();
        done.await();
        System.out.println("\nAccepted: " + accepted.get() + "  Rejected: " + rejected.get()
                + "  (expected: 2 accepted, 8 rejected)");
    }
}
