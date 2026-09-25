package com.pedidos360.users.config;

import com.pedidos360.users.model.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * Mapeo de los App Roles de Microsoft Entra ID hacia los roles internos de Pedidos360.
 * Cada propiedad agrupa los GUIDs de los app roles definidos en la App Registration.
 */
@ConfigurationProperties(prefix = "azure")
public class AzureRolesProperties {

    private List<String> admin = List.of();
    private List<String> operador = List.of();
    private List<String> cliente = List.of();

    public List<String> getAdmin() { return admin; }
    public void setAdmin(List<String> admin) { this.admin = admin; }

    public List<String> getOperador() { return operador; }
    public void setOperador(List<String> operador) { this.operador = operador; }

    public List<String> getCliente() { return cliente; }
    public void setCliente(List<String> cliente) { this.cliente = cliente; }

    public Set<String> roleIdsFor(Role role) {
        if (role == Role.ADMIN) return Set.copyOf(admin);
        if (role == Role.OPERADOR) return Set.copyOf(operador);
        if (role == Role.CLIENTE) return Set.copyOf(cliente);
        return Set.of();
    }
}