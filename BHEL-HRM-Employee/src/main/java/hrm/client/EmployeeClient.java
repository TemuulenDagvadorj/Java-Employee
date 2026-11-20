package hrm.client;

import hrm.common.AuthService;
import hrm.common.Employee;
import hrm.common.EmployeeService;
import hrm.common.FamilyMember;
import hrm.common.LeaveApplication;
import hrm.common.ServiceException;
import hrm.common.ServiceResult;
import hrm.common.SessionExpiredException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class EmployeeClient {
    private static final String HOST = System.getProperty("hrm.host", "localhost");
    private static final int PORT = Integer.getInteger("hrm.port", 1099);
    private static final boolean SSL = Boolean.getBoolean("hrm.ssl");

    private static AuthService auth;
    private static EmployeeService emp;
    private static String token;
    private static final Scanner in = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("===== BHEL HRM - EMPLOYEE PORTAL =====");
        if (!connect(3)) {
            System.out.println("Could not reach the HRM server. Please try again later.");
            return;
        }
        if (login()) {
            menu();
        }
        System.out.println("Goodbye.");
    }

    private static boolean connect(int attempts) {
        for (int i = 1; i <= attempts; i++) {
            try {
                Registry registry = SSL
                        ? LocateRegistry.getRegistry(HOST, PORT, new SslRMIClientSocketFactory())
                        : LocateRegistry.getRegistry(HOST, PORT);
                auth = (AuthService) registry.lookup(AuthService.BINDING_NAME);
                emp = (EmployeeService) registry.lookup(EmployeeService.BINDING_NAME);
                return true;
            } catch (Exception e) {
                System.out.println("Connection attempt " + i + "/" + attempts + " failed: " + e.getMessage());
                if (i < attempts) sleep(2000);
            }
        }
        return false;
    }

   
    private static boolean recover() {
        System.out.println("Connection problem. Trying to reconnect...");
        if (!connect(3)) return false;
        try {
            auth.getRole(token);
            System.out.println("Reconnected. Please repeat your last action.");
            return true;
        } catch (ServiceException e) {            // server restarted -> old token is gone
            System.out.println("The server restarted. Please log in again.");
            return login();
        } catch (RemoteException e) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    //  login

    private static boolean login() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Employee ID: ");
            String id = in.nextLine().trim();
            System.out.print("Password: ");
            String pw = readPassword();
            try {
                token = auth.login(id, pw);
                Employee me = emp.getProfile(token);
                System.out.println("\nWelcome, " + me.getFirstName() + " " + me.getLastName() + "!");
                return true;
            } catch (ServiceException e) {
                System.out.println(e.getMessage());
            } catch (RemoteException e) {
                System.out.println("Server error: " + e.getMessage());
                return false;
            }
        }
        System.out.println("Too many failed attempts.");
        return false;
    }

    private static String readPassword() {
        java.io.Console console = System.console(); 
        if (console != null) return new String(console.readPassword());
        return in.nextLine();
    }

    // menu

    private static void menu() {
        while (true) {
            System.out.println("\n===== EMPLOYEE MENU =====");
            System.out.println("1. View my profile");
            System.out.println("2. Update my profile");
            System.out.println("3. Update family details");
            System.out.println("4. Check leave balance");
            System.out.println("5. Apply for leave");
            System.out.println("6. Check leave application status");
            System.out.println("0. Logout");
            System.out.print("Choice: ");
            String choice = in.nextLine().trim();

            if (choice.equals("0")) {
                try {
                    auth.logout(token);
                } catch (RemoteException ignored) {
                    
                }
                return;
            }
            try {
                switch (choice) {
                    case "1": viewProfile(); break;
                    case "2": updateProfile(); break;
                    case "3": updateFamily(); break;
                    case "4": System.out.println("Leave balance: " + emp.getLeaveBalance(token) + " day(s)"); break;
                    case "5": applyLeave(); break;
                    case "6": showLeaveStatus(); break;
                    default: System.out.println("Invalid choice, please enter 0-6.");
                }
            } catch (SessionExpiredException e) {
                System.out.println(e.getMessage());
                if (!login()) return;
            } catch (ServiceException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (RemoteException e) {
                if (!recover()) {
                    System.out.println("Cannot reach the server. Exiting.");
                    return;
                }
            }
        }
    }

    //  features

    private static void viewProfile() throws RemoteException, ServiceException {
        Employee e = emp.getProfile(token);
        System.out.println("\n--- My profile ---");
        System.out.println("Employee ID : " + e.getEmployeeId());
        System.out.println("Name        : " + e.getFirstName() + " " + e.getLastName());
        System.out.println("IC/Passport : " + e.getIcPassport());
        System.out.println("Role        : " + e.getRole());
        System.out.println("Leave balance: " + e.getLeaveBalance() + " day(s)");
        System.out.println("Family members:");
        printFamily(e.getFamily());
    }

    private static void updateProfile() throws RemoteException, ServiceException {
        Employee e = emp.getProfile(token);
        System.out.println("Press Enter to keep the current value.");
        String first = ask("First name [" + e.getFirstName() + "]: ", e.getFirstName());
        String last = ask("Last name [" + e.getLastName() + "]: ", e.getLastName());
        String ic = ask("IC/Passport [" + e.getIcPassport() + "]: ", e.getIcPassport());
        printResult(emp.updateProfile(token, first, last, ic));
    }

    private static void updateFamily() throws RemoteException, ServiceException {
        List<FamilyMember> list = new ArrayList<>(emp.getProfile(token).getFamily());
        while (true) {
            System.out.println("\n--- Family details ---");
            printFamily(list);
            System.out.print("[A]dd  [R]emove  [S]ave  [C]ancel: ");
            String c = in.nextLine().trim().toLowerCase();
            if (c.equals("a")) {
                System.out.print("Name: ");
                String name = in.nextLine().trim();
                System.out.print("Relationship (e.g. Spouse, Child): ");
                String rel = in.nextLine().trim();
                System.out.print("IC/Passport (optional): ");
                String ic = in.nextLine().trim();
                list.add(new FamilyMember(name, rel, ic));
            } else if (c.equals("r")) {
                System.out.print("Number to remove: ");
                try {
                    int n = Integer.parseInt(in.nextLine().trim());
                    if (n >= 1 && n <= list.size()) list.remove(n - 1);
                    else System.out.println("No such number.");
                } catch (NumberFormatException ex) {
                    System.out.println("Please enter a number.");
                }
            } else if (c.equals("s")) {
                ServiceResult r = emp.updateFamilyDetails(token, list);
                printResult(r);
                if (r.isSuccess()) return;   // on failure stay in the editor so the user can fix it
            } else if (c.equals("c")) {
                System.out.println("Cancelled, nothing was changed.");
                return;
            }
        }
    }

    private static void applyLeave() throws RemoteException, ServiceException {
        System.out.println("\n--- Apply for leave ---");
        for (int i = 0; i < LeaveApplication.TYPES.length; i++) {
            System.out.println((i + 1) + ". " + LeaveApplication.TYPES[i]);
        }
        System.out.print("Leave type (number): ");
        int typeNo;
        try {
            typeNo = Integer.parseInt(in.nextLine().trim());
        } catch (NumberFormatException ex) {
            System.out.println("Please enter a number.");
            return;
        }
        if (typeNo < 1 || typeNo > LeaveApplication.TYPES.length) {
            System.out.println("Invalid leave type.");
            return;
        }
        try {
            System.out.print("Start date (yyyy-mm-dd): ");
            LocalDate start = LocalDate.parse(in.nextLine().trim());
            System.out.print("End date   (yyyy-mm-dd): ");
            LocalDate end = LocalDate.parse(in.nextLine().trim());
            System.out.print("Reason (optional): ");
            String reason = in.nextLine().trim();
            LeaveApplication app = new LeaveApplication(LeaveApplication.TYPES[typeNo - 1], start, end, reason);
            printResult(emp.applyLeave(token, app));
        } catch (DateTimeParseException ex) {
            System.out.println("Invalid date. Use the format yyyy-mm-dd, e.g. 2026-12-24.");
        }
    }

    private static void showLeaveStatus() throws RemoteException, ServiceException {
        List<LeaveApplication> list = emp.getLeaveStatus(token);
        System.out.println("\n--- My leave applications ---");
        if (list.isEmpty()) {
            System.out.println("You have no leave applications.");
            return;
        }
        System.out.printf("%-7s %-10s %-12s %-12s %-5s %-9s%n", "ID", "Type", "From", "To", "Days", "Status");
        for (LeaveApplication a : list) {
            System.out.printf("%-7s %-10s %-12s %-12s %-5d %-9s%n", a.getLeaveId(), a.getLeaveType(),
                    a.getStartDate(), a.getEndDate(), a.getDays(), a.getStatus());
        }
    }

    // helpers

    private static String ask(String prompt, String current) {
        System.out.print(prompt);
        String v = in.nextLine().trim();
        return v.isEmpty() ? current : v;
    }

    private static void printFamily(List<FamilyMember> list) {
        if (list.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            FamilyMember f = list.get(i);
            System.out.printf("  %d. %s | %s | %s%n", i + 1, f.getName(), f.getRelationship(),
                    f.getIcPassport().isEmpty() ? "-" : f.getIcPassport());
        }
    }

    private static void printResult(ServiceResult r) {
        System.out.println((r.isSuccess() ? "SUCCESS: " : "FAILED: ") + r.getMessage());
    }
}
