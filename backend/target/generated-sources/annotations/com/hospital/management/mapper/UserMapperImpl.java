package com.hospital.management.mapper;

import com.hospital.management.dto.UserDto;
import com.hospital.management.dto.auth.RegisterRequest;
import com.hospital.management.entity.User;
import java.util.Set;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-11T23:45:39+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.1 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserDto toDto(User user) {
        if ( user == null ) {
            return null;
        }

        Set<String> roles = null;
        Long id = null;
        String email = null;
        String firstName = null;
        String lastName = null;
        String phone = null;
        boolean active = false;

        roles = mapRoles( user.getRoles() );
        id = user.getId();
        email = user.getEmail();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        phone = user.getPhone();
        active = user.isActive();

        UserDto userDto = new UserDto( id, email, firstName, lastName, phone, active, roles );

        return userDto;
    }

    @Override
    public User toEntity(RegisterRequest request) {
        if ( request == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.email( request.email() );
        user.firstName( request.firstName() );
        user.lastName( request.lastName() );
        user.phone( request.phone() );

        user.active( true );

        return user.build();
    }
}
