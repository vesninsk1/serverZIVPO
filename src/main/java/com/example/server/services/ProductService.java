package com.example.server.services;

import com.example.server.entities.Product;
import com.example.server.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    
    @Transactional(readOnly = true)
    public Product getProductOrFail(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
    }
    
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    @Transactional
    public Product createProduct(String name) {
        if (productRepository.existsByName(name)) {
            throw new RuntimeException("Product with name '" + name + "' already exists");
        }
        
        Product product = Product.builder()
                .name(name)
                .isBlocked(false)
                .build();
        
        return productRepository.save(product);
    }
    
    @Transactional
    public Product blockProduct(Long productId, boolean blocked) {
        Product product = getProductOrFail(productId);
        product.setIsBlocked(blocked);
        return productRepository.save(product);
    }
}