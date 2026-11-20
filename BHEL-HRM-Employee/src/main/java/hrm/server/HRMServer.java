package hrm.server;

import hrm.common.AuthService;
import hrm.common.EmployeeService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;


public class HRMServer {
    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("hrm.port", 1099);
        boolean ssl = Boolean.getBoolean("hrm.ssl");

        RMIClientSocketFactory csf = ssl ? new SslRMIClientSocketFactory() : null;
        RMIServerSocketFactory ssf = ssl ? new SslRMIServerSocketFactory() : null;

        Registry registry = ssl ? LocateRegistry.createRegistry(port, csf, ssf)
                                : LocateRegistry.createRegistry(port);

        registry.rebind(AuthService.BINDING_NAME, new AuthServiceImpl(0, csf, ssf));
        registry.rebind(EmployeeService.BINDING_NAME, new EmployeeServiceImpl(0, csf, ssf));

        
        System.out.println("HRM server ready on port " + port + (ssl ? " (SSL/TLS ON)" : " (plain RMI)"));
        System.out.println("Demo accounts: E001/1234, E002/1234, H001/hr1234, A001/admin1234");
    }
}
