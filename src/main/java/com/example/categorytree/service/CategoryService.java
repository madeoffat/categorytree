package com.example.categorytree.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.util.MapUtils;

import com.example.categorytree.entity.Category;
import com.example.categorytree.entity.Child;
import com.example.categorytree.entity.Item;
import com.example.categorytree.repository.CategoryRepository;
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
    private RestTemplate restTemplate;

	private static final String genreUrl = "https://app.rakuten.co.jp/services/api/IchibaGenre/Search/20140222"
			+ "?format={format}"
			+ "&formatVersion={formatVersion}"
			+ "&applicationId={applicationId}"
			+ "&genrePath={genrePath}"
			+ "&genreId={genreId}";
	
	private static final String rankUrl = "https://app.rakuten.co.jp/services/api/IchibaItem/Ranking/20170628"
			+ "?format={format}"
			+ "&formatVersion={formatVersion}"
			+ "&applicationId={applicationId}"
			+ "&genreId={genreId}" 
			+ "&page={page}";

    private static final int RETRY_LIMIT = 3;
    
    private static final int ROOT = 0;
    private static final int CHILD = 1;
	private static final int GRAND_CHILD = 2;
	
	private static final String GENRE_PATH = "0";
	private static final String FORMAT_VERSION = "2";
	private static final String APPLICATION_ID = "1026301013779899297";
	private static final String FORMAT_JSON = "json";
	private static final String FETCH_PAGE_FOR_RANKING = "1";

	private static final int MAX_FETCH_FOR_RANKING = 10;
	
	private static final String GENRE_ID_KEY = "genreId";
	private static final String GENRE_LEVEL_KEY = "genreLevel";
	private static final String GENRE_NAME_KEY = "genreName";
	private static final String GENRE_PATH_KEY = "genrePath";
	private static final String FORMAT_KEY = "format";

	private static final String FORMAT_VERSION_KEY = "formatVersion";
	private static final String APPLICATION_ID_KEY = "applicationId";
	private static final String RANK_KEY = "rank";
	private static final String ITEM_NAME_KEY = "itemName";
	private static final String PAGE_KEY = "page";
		

	
	private List<Category> get(int genreId) {
		Map<String,String> params = setParamsForGenreApi(genreId);
		Map<String,Object> map = callApi(genreUrl, params);
		List<Map<String,Object>> parents = (List<Map<String, Object>>) map.get("children");
		return parents.stream()
				.map(this::mapToCategory)
				.collect(Collectors.toList());
	}
	
	private Map<String,String> setParamsForGenreApi(int genreId) {
		Map<String,String> params = new HashMap<>();
		params.put(FORMAT_KEY, FORMAT_JSON);
		params.put(FORMAT_VERSION_KEY, FORMAT_VERSION);		
		params.put(APPLICATION_ID_KEY, APPLICATION_ID);
		params.put(GENRE_ID_KEY, String.valueOf(genreId));
		params.put(GENRE_PATH_KEY, GENRE_PATH);
		return params;
	}
	
	private Map<String, Object> callApi(String url, Map<String, String> params) {
		Map<String,Object> response = new HashMap<String,Object>();
		int count = 0;
		while (count < RETRY_LIMIT) {
		  try {
		    response = restTemplate.getForObject(url, Map.class, params);
		    break;
		  } catch (RestClientException e) {
		    count++;
		    log.error("RestClientException. try to sleep.",e);
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
		List<Category> tree = get(ROOT);
		create(tree);
        return tree;
	}
	
	private Category mapToCategory(Map<String,Object> result) {
		Category category = new Category();
		category.setGenreId((int)(result.get(GENRE_ID_KEY)));
		category.setGenrePath((int)(result.get(GENRE_LEVEL_KEY)));
		category.setGenreName((String)(result.get(GENRE_NAME_KEY)));
		category.setChildren(findChildren(category));
		log.info("converted genre: {}", category.getGenreName());
		return category;
	}
	
	private List<Category> findChildren(Category category) {
		if (category.getGenrePath() > GRAND_CHILD) {
			return Collections.emptyList();
		}
	   	List<Category> children = get(category.getGenreId());
       	return children;
	}
	
    private void create(List<Category> categories) {
        categoryRepository.saveAll(toFlat(categories));
        List<Child> children =  new ArrayList<>();
        categories.forEach(g -> {
        		children.addAll(
        				g.getChildren().stream()
        				.map(c -> new Child(g.getGenreId(), c.getGenreId()))
        				.collect(Collectors.toList())
        		);
        });
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
		List<Category> children = categoryRepository.findByGenrePath(CHILD);
		if (CollectionUtils.isEmpty(children)) {
			tree();
			children = categoryRepository.findByGenrePath(CHILD);
		}
		List<Item> items = new ArrayList<>();
		children.forEach(c -> {
			Map<String, String> params = setParamsForRanking(c.getGenreId());
			Map<String, Object> result = callApi(rankUrl, params);
			if (MapUtils.isEmpty(result)) {
				log.error("could not find ranking for genreId:{}, genreName:{}", c.getGenreId(), c.getGenreName());
				return;
			}
			log.info("fetched ranking for genreId:{}", c.getGenreId());
	    		List<Map<String,Object>> resultItem = (List<Map<String, Object>>) result.get("Items");
	    		List<Item> extractedItems = resultItem.stream()
	    				.filter(r -> (int)r.get(RANK_KEY) <= MAX_FETCH_FOR_RANKING)
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
    
    private Item mapToItem(Map<String, Object> result, int genreId, String genreName) {
	    	Item item = new Item();
	    	item.setGenreId(genreId);
	    	item.setGenreName(genreName);    
		item.setRank((int)(result.get(RANK_KEY)));
    		item.setItemName((String)(result.get(ITEM_NAME_KEY)));
    		return item;
    	}
}
