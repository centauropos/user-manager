package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.PlatformUserUpdateRequest;
import com.sevenb.user_manager.dto.UserCreateRequest;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import com.sevenb.user_manager.exception.ResourceAlreadyExistsException;
import com.sevenb.user_manager.mapper.UserMapper;
import com.sevenb.user_manager.repository.PersonRepository;
import com.sevenb.user_manager.repository.RoleRepository;
import com.sevenb.user_manager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUserServiceTest {

    private static final String TENANT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleService roleService = mock(RoleService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    private final PlatformUserService service = new PlatformUserService(
            userRepository, personRepository, roleRepository, roleService, passwordEncoder, userMapper);

    @Test
    void createUserAssociatesPersonOfPathTenant() {
        Person person = person(10L);
        when(personRepository.findByTenantId(TENANT)).thenReturn(Optional.of(person));
        when(userRepository.existsByUsernameAndPerson_TenantId("cajero1", TENANT)).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        RoleEntity role = role(5L, "CAJERO");
        when(roleService.getRoleForTenant(5L, TENANT)).thenReturn(role);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        UserResponseDto dto = new UserResponseDto();
        dto.setId(99L);
        when(userMapper.userToUserDto(any(UserEntity.class))).thenReturn(dto);

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("cajero1");
        request.setPassword("secret");
        request.setRoleId(5L);

        UserResponseDto result = service.createUser(TENANT, request);

        assertThat(result.getId()).isEqualTo(99L);
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPerson()).isSameAs(person);
        assertThat(captor.getValue().getUsername()).isEqualTo("cajero1");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getRole()).isSameAs(role);
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void createUserFailsWhenPersonMissing() {
        when(personRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("cajero1");
        request.setPassword("secret");

        assertThatThrownBy(() -> service.createUser(TENANT, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empresa no encontrada");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(personRepository.findByTenantId(TENANT)).thenReturn(Optional.of(person(1L)));
        when(userRepository.existsByUsernameAndPerson_TenantId("cajero1", TENANT)).thenReturn(true);
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("cajero1");
        request.setPassword("secret");

        assertThatThrownBy(() -> service.createUser(TENANT, request))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    void getUserRejectsCrossTenant() {
        when(userRepository.findByIdAndPerson_TenantId(7L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(TENANT, 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void cannotDisableLastAdmin() {
        UserEntity admin = user(3L, "admin", role(1L, "ADMIN"), true);
        when(userRepository.findByIdAndPerson_TenantId(3L, TENANT)).thenReturn(Optional.of(admin));
        when(userRepository.countByPerson_TenantIdAndRole_NameIgnoreCase(TENANT, "ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.setEnabled(TENANT, 3L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("último usuario ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserCanToggleEnabledAndResetPassword() {
        UserEntity cajero = user(8L, "cajero", role(2L, "CAJERO"), true);
        when(userRepository.findByIdAndPerson_TenantId(8L, TENANT)).thenReturn(Optional.of(cajero));
        when(passwordEncoder.encode("nueva")).thenReturn("hashed-nueva");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserResponseDto dto = new UserResponseDto();
        dto.setId(8L);
        dto.setEnabled(false);
        when(userMapper.userToUserDto(any(UserEntity.class))).thenReturn(dto);

        PlatformUserUpdateRequest request = new PlatformUserUpdateRequest();
        request.setPassword("nueva");
        request.setEnabled(false);

        UserResponseDto result = service.updateUser(TENANT, 8L, request);

        assertThat(result.isEnabled()).isFalse();
        assertThat(cajero.getPassword()).isEqualTo("hashed-nueva");
        assertThat(cajero.isEnabled()).isFalse();
    }

    @Test
    void listRolesRequiresPerson() {
        when(personRepository.findByTenantId(TENANT)).thenReturn(Optional.of(person(1L)));
        RoleEntity role = role(1L, "ADMIN");
        when(roleRepository.findByTenantId(TENANT)).thenReturn(List.of(role));

        assertThat(service.listRoles(TENANT)).hasSize(1);
        assertThat(service.listRoles(TENANT).get(0).getName()).isEqualTo("ADMIN");
    }

    private static Person person(Long id) {
        Person person = new Person();
        person.setId(id);
        person.setTenantId(TENANT);
        person.setFirstName("Empresa");
        return person;
    }

    private static RoleEntity role(Long id, String name) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setName(name);
        role.setTenantId(TENANT);
        return role;
    }

    private static UserEntity user(Long id, String username, RoleEntity role, boolean enabled) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setPerson(person(1L));
        return user;
    }
}
