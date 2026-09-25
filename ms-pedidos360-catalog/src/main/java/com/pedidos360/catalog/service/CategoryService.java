package com.pedidos360.catalog.service;

import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.model.Category;
import com.pedidos360.catalog.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }

    public Category create(Category category) {
        if (categoryRepository.existsByName(category.getName())) {
            throw new IllegalStateException("Ya existe una categoría con el nombre: " + category.getName());
        }
        category.setId(null);
        return categoryRepository.save(category);
    }

    public Category update(Long id, Category category) {
        Category existing = findById(id);
        categoryRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(category.getName()))
                .filter(c -> !c.getId().equals(id))
                .findFirst()
                .ifPresent(c -> {
                    throw new IllegalStateException("Ya existe una categoría con el nombre: " + category.getName());
                });
        existing.setName(category.getName());
        return categoryRepository.save(existing);
    }

    public void delete(Long id) {
        Category existing = findById(id);
        if (existing.getProducts() != null && !existing.getProducts().isEmpty()) {
            throw new IllegalStateException("No se puede eliminar una categoría con productos asociados");
        }
        categoryRepository.delete(existing);
    }
}