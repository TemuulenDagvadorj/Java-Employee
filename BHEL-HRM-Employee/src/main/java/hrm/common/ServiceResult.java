package hrm.common;

import java.io.Serializable;


public class ServiceResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;

    public ServiceResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ServiceResult ok(String message) {
        return new ServiceResult(true, message);
    }

    public static ServiceResult fail(String message) {
        return new ServiceResult(false, message);
    }

    public boolean isSuccess() { return success; }

    public String getMessage() { return message; }

    @Override
    public String toString() {
        return (success ? "OK: " : "FAILED: ") + message;
    }
}
