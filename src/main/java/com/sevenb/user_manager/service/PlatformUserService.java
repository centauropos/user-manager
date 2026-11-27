package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.PlatformUserUpdateRequest;
import com.sevenb.user_manager.dto.RoleResponseDto;
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
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Operaciones de usuarios y roles sobre un tenant arbitrario (path),
 * para administradores de plataforma. No usa el tenantId del JWT como scope de negocio.
 */
@Service
public class PlatformUserService {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public PlatformUserService(UserRepository userRepository,
                               PersonRepository personRepository,
                               RoleRepository roleRepository,
                               RoleService roleService,
                               PasswordEncoder passwordEncoder,
                               UserMapper userMapper) {
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public List<UserResponseDto> listUsers(String tenantId) {
        requirePerson(tenantId);
        return userRepository.findByPerson_TenantId(tenantId).stream()
                .map(userMapper::userToUserDto)
                .toList();
    }

    @Transactional
    public UserResponseDto createUser(String tenantId, UserCreateRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("El username es obligatorio");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }

        Person person = requirePerson(tenantId);
        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameAndPerson_TenantId(username, tenantId)) {
            throw new ResourceAlreadyExistsException("El usuario ya existe: " + username);
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPerson(person);
        user.setEnabled(true);

        if (request.getRoleId() != null) {
            user.setRole(roleService.getRoleForTenant(request.getRoleId(), tenantId));
        }

        return userMapper.userToUserDto(userRepository.save(user));
    }

    public UserResponseDto getUser(String tenantId, Long userId) {
        return userMapper.userToUserDto(requireUser(tenantId, userId));
    }

    @Transactional
    public UserResponseDto updateUser(String tenantId, Long userId, PlatformUserUpdateRequest request) {
        UserEntity user = requireUser(tenantId, userId);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRoleId() != null) {
            applyRoleChange(tenantId, user, request.getRoleId());
        }
        if (request.getEnabled() != null) {
            applyEnabled(tenantId, user, request.getEnabled());
        }

        return userMapper.userToUserDto(userRepository.save(user));
    }

    @Transactional
    public UserResponseDto resetPassword(String tenantId, Long userId, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        UserEntity user = requireUser(tenantId, userId);
        user.setPassword(passwordEncoder.encode(password));
        return userMapper.userToUserDto(userRepository.save(user));
    }

    @Transactional
    public UserResponseDto setEnabled(String tenantId, Long userId, boolean enabled) {
        UserEntity user = requireUser(tenantId, userId);
        applyEnabled(tenantId, user, enabled);
        return userMapper.userToUserDto(userRepository.save(user));
    }

    @Transactional
    public UserResponseDto assignRole(String tenantId, Long userId, Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("El roleId es obligatorio");
        }
        UserEntity user = requireUser(tenantId, userId);
        applyRoleChange(tenantId, user, roleId);
        return userMapper.userToUserDto(userRepository.save(user));
    }

    public List<RoleResponseDto> listRoles(String tenantId) {
        requirePerson(tenantId);
        return roleRepository.findByTenantId(tenantId).stream()
                .map(this::toRoleDto)
                .toList();
    }

    private void applyRoleChange(String tenantId, UserEntity user, Long roleId) {
        RoleEntity newRole = roleService.getRoleForTenant(roleId, tenantId);
        if (user.getRole() != null && newRole.getId().equals(user.getRole().getId())) {
            return;
        }
        if (RoleService.isAdminRole(user.getRole()) && !RoleService.isAdminRole(newRole)) {
            assertNotLastAdmin(tenantId, "No se puede quitar el rol ADMIN al último usuario ADMIN de la empresa");
        }
        user.setRole(newRole);
    }

    private void applyEnabled(String tenantId, UserEntity user, boolean enabled) {
        if (user.isEnabled() == enabled) {
            return;
        }
        if (!enabled && RoleService.isAdminRole(user.getRole())) {
            assertNotLastAdmin(tenantId, "No se puede deshabilitar al último usuario ADMIN de la empresa");
        }
        user.setEnabled(enabled);
    }

    private void assertNotLastAdmin(String tenantId, String message) {
        long admins = userRepository.countByPerson_TenantIdAndRole_NameIgnoreCase(tenantId, RoleService.ADMIN_ROLE);
        if (admins <= 1) {
            throw new IllegalArgumentException(message);
        }
    }

    private Person requirePerson(String tenantId) {
        requireTenantId(tenantId);
        return personRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada para tenant: " + tenantId));
    }

    private UserEntity requireUser(String tenantId, Long userId) {
        requireTenantId(tenantId);
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("ID de usuario inválido");
        }
        return userRepository.findByIdAndPerson_TenantId(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));
    }

    private static void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId es obligatorio");
        }
    }

    private RoleResponseDto toRoleDto(RoleEntity role) {
        RoleResponseDto dto = new RoleResponseDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setTenantId(role.getTenantId());
        return dto;
    }
}
