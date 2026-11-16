package ma.sanai3i.security;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Permission;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Checks staff permissions on every administration call (read from the database: changes apply at once). */
@Component
public class StaffAccess {
    private final UserRepository users;

    public StaffAccess(UserRepository users) {
        this.users = users;
    }

    public User staff(AuthUser me) {
        User u = me == null ? null : users.findById(me.userId()).orElse(null);
        if (u == null || u.role != Role.ADMIN || u.blocked) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERMISSION");
        }
        return u;
    }

    public User require(AuthUser me, Permission p) {
        User u = staff(me);
        if (!u.superAdmin && !u.permissionSet().contains(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_PERMISSION");
        }
        return u;
    }

    public boolean has(AuthUser me, Permission p) {
        try {
            require(me, p);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    public User owner(AuthUser me) {
        User u = staff(me);
        if (!u.superAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY");
        }
        return u;
    }
}
