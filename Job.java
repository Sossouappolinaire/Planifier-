package bj.planificateur;

public class Job {
    public final long id;
    public final String group;
    public final String message;

    public Job(long id, String group, String message) {
        this.id = id;
        this.group = group;
        this.message = message;
    }
}
