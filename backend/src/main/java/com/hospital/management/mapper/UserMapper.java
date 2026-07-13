package com.hospital.management.mapper;

import com.hospital.management.dto.UserDto;
import com.hospital.management.dto.auth.RegisterRequest;
import com.hospital.management.entity.Role;
import com.hospital.management.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRoles")
    UserDto toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "passwordHash", ignore = true) // to be manually encoded
    User toEntity(RegisterRequest request);

    @Named("mapRoles")
    default Set<String> mapRoles(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }
}
