package Project.commercial.service;

import Project.commercial.domain.*;
import Project.commercial.dto.item.*;
import Project.commercial.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;
    private final DetailImageRepository detailImageRepository;
    private final CategoryRepository categoryRepository;
    private final DetailCategoryRepository detailCategoryRepository;
    private final SizeRepository sizeRepository;
    private final ColorRepository colorRepository;
    private final ThumbnailImageRepository thumbnailImageRepository;
    @Value("$(file.dir)")
    String fileDir;

    public ItemCreateResponseDto create(ItemCreateRequestDto itemCreateRequestDto, MultipartFile thumbnailImage, List<MultipartFile> detailImages) throws IOException {
        validateThumbnailImage(thumbnailImage);

        DetailCategory detailCategory = findByIdOrThrow(detailCategoryRepository, itemCreateRequestDto.getDetailCategory_id(), "Invalid Detail Category ID");
        Category category = findByIdOrThrow(categoryRepository, itemCreateRequestDto.getCategory_id(), "Invalid Category ID");
        Color color = findByIdOrThrow(colorRepository, itemCreateRequestDto.getColor_id(), "Invalid Color ID");
        Size size = findByIdOrThrow(sizeRepository, itemCreateRequestDto.getSize_id(), "Invalid Size ID");

        Item newItem = createNewItem(itemCreateRequestDto, detailCategory, category, color, size);
        ThumbnailImage thumbnailImageEntity = saveThumbnailImage(thumbnailImage, newItem);

        if (!CollectionUtils.isEmpty(detailImages)) {
            saveDetailImages(detailImages, newItem);
        }

        return ItemCreateResponseDto.builder()
                .id(newItem.getId())
                .category(newItem.getCategory())
                .detailCategory(newItem.getDetailCategory())
                .itemName(newItem.getItemName())
                .description(newItem.getDescription())
                .color(newItem.getColor())
                .size(newItem.getSize())
                .price(comma(newItem.getPrice()))
                .thumbnailImage(thumbnailImageEntity)
                .detailImage(newItem.getDetailImage())
                .build();
    }

    public List<ItemDto> list(Pageable pageable) {
        Page<Item> items = itemRepository.findAll(pageable);
        return items.stream()
                .map(item -> ItemDto.builder()
                        .id(item.getId())
                        .category(item.getCategory())
                        .detailCategory(item.getDetailCategory())
                        .itemName(item.getItemName())
                        .description(item.getDescription())
                        .size(item.getSize())
                        .color(item.getColor())
                        .price(comma(item.getPrice()))
                        .thumbnailImage(item.getThumbnailImage())
                        .build())
                .collect(Collectors.toList());
    }

    public List<ItemDto> search(ItemSearchConditionDto itemSearchConditionDto, Pageable pageable) {
        List<Item> itemList = itemRepository.searchItem(itemSearchConditionDto, pageable);
        return itemList.stream()
                .map(item -> ItemDto.builder()
                        .id(item.getId())
                        .category(item.getCategory())
                        .detailCategory(item.getDetailCategory())
                        .itemName(item.getItemName())
                        .description(item.getDescription())
                        .size(item.getSize())
                        .color(item.getColor())
                        .price(comma(item.getPrice()))
                        .thumbnailImage(item.getThumbnailImage())
                        .build())
                .collect(Collectors.toList());
    }

    public ItemDto detailPage(Map<String, Long> item_id_map) {
        Long itemId = item_id_map.get("item_id");
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new RuntimeException("Item not found"));

        return ItemDto.builder()
                .id(item.getId())
                .category(item.getCategory())
                .detailCategory(item.getDetailCategory())
                .itemName(item.getItemName())
                .description(item.getDescription())
                .size(item.getSize())
                .color(item.getColor())
                .price(comma(item.getPrice()))
                .thumbnailImage(item.getThumbnailImage())
                .detailImage(item.getDetailImage())
                .build();
    }

    public void delete(Map<String, Long> item_id_map) {
        Long itemId = item_id_map.get("item_id");
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new RuntimeException("Item not found"));

        itemRepository.delete(item);
    }

    public ItemModifiedResponseDto modified(ItemModifiedRequestDto itemModifiedRequestDto, MultipartFile newThumbnailImage, List<MultipartFile> newDetailImages) throws IOException {
        Item item = itemRepository.findById(itemModifiedRequestDto.getId()).orElseThrow(() -> new RuntimeException("Item not found"));

        validateThumbnailImage(newThumbnailImage);

        // Handle thumbnail image update
        if (thumbnailImageRepository.findByItem_id(item.getId()).isPresent()) {
            thumbnailImageRepository.deleteByItem_id(item.getId());
        }
        ThumbnailImage thumbnailImageEntity = saveThumbnailImage(newThumbnailImage, item);

        // Handle detail images update
        List<DetailImage> existingDetailImages = item.getDetailImage();
        if (CollectionUtils.isEmpty(existingDetailImages) && !CollectionUtils.isEmpty(newDetailImages)) {
            saveDetailImages(newDetailImages, item);
        } else if (!CollectionUtils.isEmpty(existingDetailImages) && !CollectionUtils.isEmpty(newDetailImages)) {
            detailImageRepository.deleteByItem_id(item.getId());
            saveDetailImages(newDetailImages, item);
        }

        DetailCategory detailCategory = findByIdOrThrow(detailCategoryRepository, itemModifiedRequestDto.getDetailCategory_id(), "Invalid Detail Category ID");
        Category category = findByIdOrThrow(categoryRepository, itemModifiedRequestDto.getCategory_id(), "Invalid Category ID");
        Color color = findByIdOrThrow(colorRepository, itemModifiedRequestDto.getColor_id(), "Invalid Color ID");
        Size size = findByIdOrThrow(sizeRepository, itemModifiedRequestDto.getSize_id(), "Invalid Size ID");

        item.updateItem(ItemModifiedResponseDto.builder()
                .id(item.getId())
                .itemName(itemModifiedRequestDto.getItemName())
                .description(itemModifiedRequestDto.getDescription())
                .detailCategory(detailCategory)
                .category(category)
                .size(size)
                .color(color)
                .price(itemModifiedRequestDto.getPrice())
                .thumbnailImage(thumbnailImageEntity)
                .detailImage(detailImageRepository.findAllByItem_id(item.getId()))
                .build());

        return ItemModifiedResponseDto.builder()
                .id(item.getId())
                .itemName(itemModifiedRequestDto.getItemName())
                .description(itemModifiedRequestDto.getDescription())
                .detailCategory(detailCategory)
                .category(category)
                .size(size)
                .color(color)
                .price(itemModifiedRequestDto.getPrice())
                .thumbnailImage(thumbnailImageEntity)
                .detailImage(detailImageRepository.findAllByItem_id(item.getId()))
                .build();
    }

    // Helper methods

    private ThumbnailImage saveThumbnailImage(MultipartFile thumbnailImage, Item item) throws IOException {
        String savedFileName = createSaveFileName(thumbnailImage.getOriginalFilename());
        thumbnailImage.transferTo(new File(getFullPath(savedFileName)));

        ThumbnailImage thumbnailImageEntity = ThumbnailImage.builder()
                .uploadImageName(thumbnailImage.getOriginalFilename())
                .storeImageName(savedFileName)
                .fileSize(thumbnailImage.getSize())
                .item(item)
                .build();

        return thumbnailImageRepository.save(thumbnailImageEntity);
    }

    private void saveDetailImages(List<MultipartFile> detailImages, Item item) throws IOException {
        for (MultipartFile detailImage : detailImages) {
            validateImageType(detailImage.getContentType());

            String savedFileName = createSaveFileName(detailImage.getOriginalFilename());
            detailImage.transferTo(new File(getFullPath(savedFileName)));

            DetailImage detailImageEntity = DetailImage.builder()
                    .uploadImageName(detailImage.getOriginalFilename())
                    .storeImageName(savedFileName)
                    .fileSize(detailImage.getSize())
                    .item(item)
                    .build();

            detailImageRepository.save(detailImageEntity);
        }
    }

    private Item createNewItem(ItemCreateRequestDto dto, DetailCategory detailCategory, Category category, Color color, Size size) {
        Item item = Item.builder()
                .itemName(dto.getItemName())
                .detailCategory(detailCategory)
                .category(category)
                .description(dto.getDescription())
                .price(dto.getPrice())
                .color(color)
                .size(size)
                .build();

        return itemRepository.save(item);
    }

    private <T> T findByIdOrThrow(JpaRepository<T, Long> repository, Long id, String errorMessage) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    private void validateThumbnailImage(MultipartFile thumbnailImage) {
        if (thumbnailImage == null || thumbnailImage.isEmpty()) {
            throw new IllegalArgumentException("Thumbnail image is required.");
        }

        if (isValidImageType(thumbnailImage.getContentType())) {
            throw new IllegalArgumentException("Invalid thumbnail image type.");
        }
    }

    private void validateImageType(String contentType) {
        if (isValidImageType(contentType)) {
            throw new IllegalArgumentException("Invalid image type.");
        }
    }

    private boolean isValidImageType(String contentType) {
        return !"image/jpeg".equals(contentType) && !"image/png".equals(contentType) && !"image/gif".equals(contentType);
    }

    private String createSaveFileName(String originalFileName) {
        String ext = extractExt(originalFileName);
        String uuid = UUID.randomUUID().toString();
        return uuid + "." + ext;
    }

    private String extractExt(String originalFileName) {
        int index = originalFileName.lastIndexOf(".");
        return originalFileName.substring(index + 1);
    }

    private String getFullPath(String fileName) {
        return fileDir + fileName;
    }

    private String comma(int value) {
        DecimalFormat df = new DecimalFormat("###,###");
        return df.format(value);
    }



}
