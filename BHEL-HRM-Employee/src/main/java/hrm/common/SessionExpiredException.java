package hrm.common;


public class SessionExpiredException extends ServiceException {
    private static final long serialVersionUID = 1L;

    public SessionExpiredException() {
        super("Your session has expired or is invalid. Please log in again.");
    }
}
