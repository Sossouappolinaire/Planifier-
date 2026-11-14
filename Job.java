package bj.planificateur;

public class Job {
    public final long id;
    public final String group;
    public final String message;
    /** Si renseigné : ouvre directement ce lien WhatsApp (wa.me) puis appuie sur Envoyer tout seul. */
    public final String uri;
    /** Lien d'invitation du groupe (chat.whatsapp.com) : ouvre directement la discussion du groupe. */
    public final String link;
    /** 0 = premier essai, 1 et 2 = réessais automatiques. */
    public final int attempt;

    public Job(long id, String group, String message) {
        this(id, group, message, null, null);
    }

    public Job(long id, String group, String message, String uri) {
        this(id, group, message, uri, null);
    }

    public Job(long id, String group, String message, String uri, String link) {
        this(id, group, message, uri, link, 0);
    }

    public Job(long id, String group, String message, String uri, String link, int attempt) {
        this.attempt = attempt;
        this.id = id;
        this.group = group;
        this.message = message;
        this.uri = uri;
        this.link = link;
    }
}
