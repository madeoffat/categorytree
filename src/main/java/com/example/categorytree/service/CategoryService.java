package com.example.categorytree.service;

import static com.example.categorytree.Constants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.util.MapUtils;

import com.example.categorytree.entity.Category;
import com.example.categorytree.entity.ChildRelation;
import com.example.categorytree.entity.Item;
import com.example.categorytree.repository.CategoryRepository;
import com.example.categorytree.repository.ChildRelationRepository;
import com.example.categorytree.repository.ItemRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CategoryService {

	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private ItemRepository itemRepository;
	@Autowired
	private ChildRelationRepository childRelationRepository;
	@Autowired
	private RestTemplate restTemplate;

	private static final String genreUrl = "https://app.rakuten.co.jp/services/api/IchibaGenre/Search/20140222"
			+ "?format={format}" + "&formatVersion={formatVersion}"
			+ "&applicationId={applicationId}" + "&genrePath={genrePath}"
			+ "&genreId={genreId}";

	private static final String rankUrl = "https://app.rakuten.co.jp/services/api/IchibaItem/Ranking/20170628"
			+ "?format={format}" + "&formatVersion={formatVersion}"
			+ "&applicationId={applicationId}" + "&genreId={genreId}"
			+ "&page={page}";

	private List<Category> getTree(int genreId) {
		Map<String, String> params = setParamsForGenreApi(genreId);
		Map<String, Object> map = callApi(genreUrl, params);
		List<Map<String, Object>> parents = (List<Map<String, Object>>) map
				.get("children");
		return parents.stream().map(this::mapToCategory)
				.sorted(Comparator.comparing(Category::getGenreId))
				.collect(Collectors.toList());
	}

	private List<Category> getTreeFromDb() {
		List<Category> categories = categoryRepository
				.findAll(new Sort(Sort.Direction.ASC, "genreId"));
		List<ChildRelation> relations = childRelationRepository
				.findAll(new Sort(Sort.Direction.ASC, "genreId"));
		if (CollectionUtils.isEmpty(categories)
				|| CollectionUtils.isEmpty(relations)) {
			return Collections.emptyList();
		}
		return createTrees(categories, relations);
	}

	private List<Category> createTrees(List<Category> categories,
			List<ChildRelation> relations) {
		List<Category> categoriesWithChildren = new ArrayList<>();
		List<Category> parents = categories.stream()
				.filter(c -> c.getGenrePath() == PARENT)
				.collect(Collectors.toList());
		parents.forEach(p -> {
			categoriesWithChildren.add(createTree(p, categories, relations));
		});
		return categoriesWithChildren;
	}

	private Category createTree(Category parent, List<Category> categories,
			List<ChildRelation> relations) {
		List<Category> children = getChildren(parent.getGenreId(), categories,
				relations);
		children.forEach(c -> {
			c.setChildren(getChildren(c.getGenreId(), categories, relations));
		});
		parent.setChildren(children);
		return parent;
	}

	private List<Category> getChildren(int parentGenreId,
			List<Category> categories, List<ChildRelation> relations) {
		List<Integer> childrenIdSet = relations.stream()
				.filter(r -> parentGenreId == r.getGenreId())
				.map(ChildRelation::getChildGenreId)
				.collect(Collectors.toList());
		List<Category> children = categories.stream()
				.filter(c -> childrenIdSet.contains(c.getGenreId()))
				.collect(Collectors.toList());
		return children;
	}

	private Map<String, String> setParamsForGenreApi(int genreId) {
		Map<String, String> params = new HashMap<>();
		params.put(FORMAT_KEY, FORMAT_JSON);
		params.put(FORMAT_VERSION_KEY, FORMAT_VERSION);
		params.put(APPLICATION_ID_KEY, APPLICATION_ID);
		params.put(GENRE_ID_KEY, String.valueOf(genreId));
		params.put(GENRE_PATH_KEY, GENRE_PATH);
		return params;
	}

	private Map<String, Object> callApi(String url,
			Map<String, String> params) {
		Map<String, Object> response = new HashMap<String, Object>();
		int count = 0;
		while (count < RETRY_LIMIT) {
			try {
				response = restTemplate.getForObject(url, Map.class, params);
				break;
			} catch (RestClientException e) {
				count++;
				log.error("RestClientException. try to sleep.", e);
				sleep();
			}
		}
		return response;
	}

	private void sleep() {
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			log.error("faild to sleep", e);
		}
	}

	public List<Category> tree() {
		List<Category> tree = getTreeFromDb();
		if (CollectionUtils.isEmpty(tree)) {
			tree = getTree(ROOT);
		}
		save(tree);
		return tree;
	}

	private Category mapToCategory(Map<String, Object> result) {
		Category category = new Category();
		category.setGenreId((int) (result.get(GENRE_ID_KEY)));
		category.setGenrePath((int) (result.get(GENRE_LEVEL_KEY)));
		category.setGenreName((String) (result.get(GENRE_NAME_KEY)));
		category.setChildren(findChildren(category));
		log.info("converted genre: {}", category.getGenreName());
		return category;
	}

	private List<Category> findChildren(Category category) {
		if (category.getGenrePath() >= GRAND_CHILD) {
			return Collections.emptyList();
		}
		List<Category> children = getTree(category.getGenreId());
		return children;
	}

	private void save(List<Category> categories) {
		List<Category> flatCategories = toFlat(categories);
		categoryRepository.saveAll(flatCategories);
		List<ChildRelation> allRelations = new ArrayList<>();
		flatCategories.stream().filter(c -> c.getGenrePath() <= CHILD)
				.forEach(cate -> {
					List<ChildRelation> relations = cate.getChildren().stream()
							.map(c -> {
								final ChildRelation r = new ChildRelation();
								r.setGenreId(cate.getGenreId());
								r.setChildGenreId(c.getGenreId());
								log.info("genreid: {}, childGenreId: {}",
										cate.getGenreId(), c.getGenreId());
								return r;
							}).collect(Collectors.toList());
					allRelations.addAll(relations);
				});
		childRelationRepository.saveAll(allRelations);
	}

	private List<Category> toFlat(List<Category> categories) {
		List<Category> flatList = new ArrayList<>();
		List<Category> children = extractChildren(categories);
		List<Category> grandChildren = extractChildren(children);
		flatList.addAll(categories);
		flatList.addAll(children);
		flatList.addAll(grandChildren);
		return flatList;
	}

	private List<Category> extractChildren(List<Category> categories) {
		List<Category> children = new ArrayList<>();
		categories.forEach(c -> {
			if (!CollectionUtils.isEmpty(c.getChildren())) {
				children.addAll(c.getChildren());
			}
		});
		return children;
	}

	public Map<String, List<Item>> ranking() {
		List<Item> items = getItemsWithRank();
		itemRepository.saveAll(items);
		Map<String, List<Item>> rankMap = items.stream()
				.collect(Collectors.groupingBy(Item::getGenreName));
		return rankMap;
	}

	private List<Item> getItemsWithRank() {
		List<Category> categories = categoryRepository.findByGenrePath(PARENT);
		if (CollectionUtils.isEmpty(categories)) {
			tree();
			categories = categoryRepository.findByGenrePath(PARENT);
		}
		List<Item> items = new ArrayList<>();
		categories.forEach(c -> {
			Map<String, String> params = setParamsForRanking(c.getGenreId());
			Map<String, Object> result = callApi(rankUrl, params);
			if (MapUtils.isEmpty(result)) {
				log.error("could not find ranking for genreId:{}, genreName:{}",
						c.getGenreId(), c.getGenreName());
				return;
			}
			log.info("fetched ranking for genreId:{}", c.getGenreId());
			List<Map<String, Object>> resultItem = (List<Map<String, Object>>) result
					.get("Items");
			List<Item> extractedItems = resultItem.stream()
					.filter(r -> (int) r.get(RANK_KEY) <= MAX_FETCH_FOR_RANKING)
					.map(r -> mapToItem(r, c.getGenreId(), c.getGenreName()))
					.sorted(Comparator.comparing(Item::getRank))
					.collect(Collectors.toList());
			items.addAll(extractedItems);
		});
		return items;
	}

	private Map<String, String> setParamsForRanking(int genreId) {
		Map<String, String> params = new HashMap<>();
		params.put(FORMAT_KEY, FORMAT_JSON);
		params.put(FORMAT_VERSION_KEY, FORMAT_VERSION);
		params.put(APPLICATION_ID_KEY, APPLICATION_ID);
		params.put(GENRE_ID_KEY, String.valueOf(genreId));
		params.put(PAGE_KEY, FETCH_PAGE_FOR_RANKING);
		return params;
	}

	public static Item mapToItem(Map<String, Object> result, int genreId,
			String genreName) {
		return Item.builder().genreId(genreId).genreName(genreName)
				.rank((int) (result.get(RANK_KEY)))
				.itemName((String) (result.get(ITEM_NAME_KEY))).build();
	}

}
