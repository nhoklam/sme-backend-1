package sme.backend.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sme.backend.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * UserPrincipal - Wrapper bọc User entity vào Spring Security UserDetails.
 * Object này sống trong SecurityContext sau khi authenticate thành công.
 * Từ đây có thể lấy userId, warehouseId, role để phân quyền và phân luồng dữ liệu.
 */
@AllArgsConstructor
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String password;
    private final String fullName;
    private final UUID warehouseId;     // NULL cho ADMIN
    private final User.UserRole role;
    private final boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal build(User user) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(user.getRole().name())
        );
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getWarehouseId(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsActive()),
                authorities
        );
    }

    @Override
    public boolean isAccountNonExpired()    { return true; }

    @Override
    public boolean isAccountNonLocked()     { return isActive; }

    @Override
    public boolean isCredentialsNonExpired(){ return true; }

    @Override
    public boolean isEnabled()              { return isActive; }
}
