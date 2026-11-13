package bj.planificateur;

public class Job {
    public final long id;
    public final String group;
    public final String message;
    /** Si renseigné : ouvre directement ce lien WhatsApp (wa.me) puis appuie sur Envoyer tout seul. */
    public final String uri;

    public Job(long id, String group, String message) {
        this(id, group, message, null);
    }

    public Job(long id, String group, String message, String uri) {
        this.id = id;
        this.group = group;
        this.message = message;
        this.uri = uri;
    }
}
