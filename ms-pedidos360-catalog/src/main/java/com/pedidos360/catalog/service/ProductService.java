package com.pedidos360.catalog.service;

import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public List<Product> findAll(Long categoryId) {
        return categoryId == null
                ? productRepository.findAll()
                : productRepository.findByCategoryId(categoryId);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    public Product create(Product product) {
        product.setId(null);
        product.setCategory(categoryService.findById(product.getCategory().getId()));
        return productRepository.save(product);
    }

    public Product update(Long id, Product product) {
        Product existing = findById(id);
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        existing.setStock(product.getStock());
        existing.setCategory(categoryService.findById(product.getCategory().getId()));
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        productRepository.delete(findById(id));
    }

    public Product applyStockChange(Long id, int delta) {
        Product product = findById(id);
        int newStock = product.getStock() + delta;
        if (newStock < 0) {
            throw new IllegalStateException("Stock insuficiente para el producto: " + product.getName());
        }
        product.setStock(newStock);
        return productRepository.save(product);
    }
}