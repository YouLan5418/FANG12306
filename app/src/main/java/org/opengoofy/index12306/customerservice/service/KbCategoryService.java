package org.opengoofy.index12306.customerservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.dao.entity.KbCategoryDO;
import org.opengoofy.index12306.customerservice.dao.entity.KbDocumentDO;
import org.opengoofy.index12306.customerservice.dao.mapper.KbCategoryMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KbDocumentMapper;
import org.opengoofy.index12306.customerservice.dto.req.KbCategoryCreateReqDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KbCategoryRespDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库分类服务，提供分类的增删改查。
 */
@Service
@RequiredArgsConstructor
public class KbCategoryService {

    private final KbCategoryMapper categoryMapper;
    private final KbDocumentMapper docMapper;

    public Long createCategory(KbCategoryCreateReqDTO requestParam) {
        KbCategoryDO category = new KbCategoryDO();
        category.setName(requestParam.getName());
        category.setDescription(requestParam.getDescription());
        categoryMapper.insert(category);
        return category.getId();
    }

    public List<KbCategoryRespDTO> listCategories() {
        List<KbCategoryDO> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<KbCategoryDO>()
                        .orderByAsc(KbCategoryDO::getId));
        return categories.stream()
                .map(c -> KbCategoryRespDTO.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .documentCount(docMapper.selectCount(new LambdaQueryWrapper<KbDocumentDO>()
                                .eq(KbDocumentDO::getCategoryId, c.getId())))
                        .build())
                .toList();
    }

    public void updateCategory(Long id, KbCategoryCreateReqDTO requestParam) {
        KbCategoryDO category = new KbCategoryDO();
        category.setId(id);
        category.setName(requestParam.getName());
        category.setDescription(requestParam.getDescription());
        categoryMapper.updateById(category);
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }
}