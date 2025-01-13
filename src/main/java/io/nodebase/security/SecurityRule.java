package io.nodebase.security;

public final class SecurityRule {

    public enum Operation { READ, WRITE, DELETE, ALL }
    public enum Condition { PUBLIC, AUTHENTICATED, OWNER, ADMIN }

    private String resource;
    private Operation operation;
    private Condition condition;
    private boolean enabled;

    public SecurityRule() {
    }

    public SecurityRule(String resource, Operation operation, Condition condition, boolean enabled) {
        this.resource = resource;
        this.operation = operation;
        this.condition = condition;
        this.enabled = enabled;
    }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public Condition getCondition() { return condition; }
    public void setCondition(Condition condition) { this.condition = condition; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
