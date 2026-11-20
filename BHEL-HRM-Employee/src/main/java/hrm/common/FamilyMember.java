package hrm.common;

import java.io.Serializable;


public class FamilyMember implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String relationship;
    private final String icPassport; // optional, "" when not given

    public FamilyMember(String name, String relationship, String icPassport) {
        this.name = name;
        this.relationship = relationship;
        this.icPassport = icPassport == null ? "" : icPassport;
    }

    public String getName() { return name; }

    public String getRelationship() { return relationship; }

    public String getIcPassport() { return icPassport; }

    @Override
    public String toString() {
        return name + " (" + relationship + ")";
    }
}
