package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A file uploaded by a user. The id is a random UUID, also used in the public URL /media/{id}. */
@Entity
@Table(name = "media")
public class Media {
    /** REQUEST, AVATAR and PORTFOLIO are public (unguessable URL); ID_FRONT and ID_BACK are only for the owner and admins. */
    public static final String REQUEST = "REQUEST";
    public static final String AVATAR = "AVATAR";
    public static final String PORTFOLIO = "PORTFOLIO";
    public static final String ID_FRONT = "ID_FRONT";
    public static final String ID_BACK = "ID_BACK";
    /** Job evidence, linked to the request: photos before / after the work, a receipt, a photo sent with a problem. */
    public static final String JOB_BEFORE = "JOB_BEFORE";
    public static final String JOB_AFTER = "JOB_AFTER";
    public static final String RECEIPT = "RECEIPT";
    public static final String CASE = "CASE";

    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "owner_id", nullable = false)
    public Long ownerId;

    @Column(nullable = false, length = 20)
    public String purpose;

    /** IMAGE, VIDEO or AUDIO */
    @Column(nullable = false, length = 10)
    public String kind;

    @Column(nullable = false, length = 80)
    public String mime;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(name = "file_name", nullable = false, length = 120)
    public String fileName;

    @Column(name = "request_id")
    public Long requestId;

    @Column(name = "artisan_id")
    public Long artisanId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public boolean isPrivate() {
        return ID_FRONT.equals(purpose) || ID_BACK.equals(purpose);
    }
}
