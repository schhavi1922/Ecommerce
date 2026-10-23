package com.infominez.ecommerce.product;

import com.infominez.ecommerce.common.exception.ResourceNotFoundException;
import com.infominez.ecommerce.product.dto.ProductResponse;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository) { this.repository = repository; }

    public List<ProductResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(ProductResponse::from).toList();
    }

    public ProductResponse get(long id) {
        return ProductResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " was not found")));
    }
}
