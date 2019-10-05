/*
 * Copyright (c) 2018. The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package bio.overture.rollcall.config;

import bio.overture.rollcall.jwt.JWTAuthorizationFilter;
import com.auth0.spring.security.api.JwtWebSecurityConfigurer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;

@Slf4j
@EnableWebSecurity
@EnableResourceServer
@Profile("!test")
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${auth0.apiAudience}")
    private String audience;

    @Value("${auth0.issuer}")
    private String issuer;

    @Override
    @SneakyThrows
    public void configure(HttpSecurity http) {
        JwtWebSecurityConfigurer
                .forRS256(audience, issuer)
                .configure(http)
                .authorizeRequests()
                .antMatchers("/swagger**", "/swagger-resources/**", "/v2/api**", "/webjars/**").permitAll()
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.POST).authenticated()
                .and()
                .authorizeRequests()
                .anyRequest().authenticated()
                .and()
                .addFilterAfter(new JWTAuthorizationFilter(), BasicAuthenticationFilter.class);

    }

    @Bean
    @SuppressWarnings("unchecked")
    public FilterRegistrationBean simpleCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean bean = new FilterRegistrationBean(new org.springframework.web.filter.CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }


}
