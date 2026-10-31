package bench.search;

import bench.document.PlaceDocument;
import bench.domain.Place;
import bench.repository.PlaceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    public static final int PAGE_SIZE = 10;

    private final PlaceRepository placeRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public SearchService(PlaceRepository placeRepository, ElasticsearchOperations elasticsearchOperations) {
        this.placeRepository = placeRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public SearchOutcome search(Strategy strategy, String search, int page) {
        PageRequest pageRequest = PageRequest.of(page - 1, PAGE_SIZE);
        return switch (strategy) {
            case LIKE -> fromPlaces(placeRepository.findByNameContainingOrAddressContaining(search, pageRequest));
            case ES_N1 -> esWithFindById(search, pageRequest);
            case FULLTEXT -> fulltext(search, pageRequest);
            case MY -> myVersion(search, pageRequest);
        };
    }

    private SearchHits<PlaceDocument> esSearch(String search, PageRequest pageRequest) {
        Query searchQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .should(s -> s
                                        .match(m -> m
                                                .field("name")
                                                .query(search)
                                        )
                                )
                                .should(s -> s
                                        .match(m -> m
                                                .field("address")
                                                .query(search)
                                        )
                                )
                        )
                )
                .withPageable(pageRequest)
                .build();
        return elasticsearchOperations.search(searchQuery, PlaceDocument.class);
    }

    private SearchOutcome esWithFindById(String search, PageRequest pageRequest) {
        SearchHits<PlaceDocument> searchHits = esSearch(search, pageRequest);
        List<Place> placeList = searchHits.stream()
                .map(hit -> placeRepository.findById(hit.getContent().getId())
                        .orElseThrow(() -> new EntityNotFoundException("Place not found with id: " + hit.getContent().getId())))
                .collect(Collectors.toList());
        return fromPlaces(new PageImpl<>(placeList, pageRequest, searchHits.getTotalHits()));
    }

    private SearchOutcome fulltext(String search, PageRequest pageRequest) {
        String cleaned = search.replaceAll("[+\\-<>()~*\"@]", " ").trim();
        if (cleaned.isEmpty()) {
            return new SearchOutcome(0, List.of(), List.of());
        }
        return fromPlaces(placeRepository.searchFulltext("\"" + cleaned + "\"", pageRequest));
    }

    private SearchOutcome fromPlaces(Page<Place> page) {
        List<Place> places = page.getContent();
        return new SearchOutcome(
                page.getTotalElements(),
                places.stream().map(Place::getId).toList(),
                places.stream().map(Place::getName).toList());
    }

    private SearchOutcome myVersion(String search, PageRequest pageRequest) {
        SearchHits<PlaceDocument> searchHits = esSearch(search, pageRequest);
        List<Long> ids = new ArrayList<>();
        for (SearchHit<PlaceDocument> hit : searchHits) {
            ids.add(hit.getContent().getId());
        }
        List<Place> placeList = placeRepository.findAllById(ids);

        placeList.sort(Comparator.comparing(place -> ids.indexOf(place.getId())));
        return fromPlaces(new PageImpl<>(placeList, pageRequest, searchHits.getTotalHits()));
    }
}
