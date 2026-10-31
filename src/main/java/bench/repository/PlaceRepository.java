package bench.repository;

import bench.domain.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query("SELECT p FROM Place p WHERE" +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))" +
            "OR (LOWER(p.address) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Place> findByNameContainingOrAddressContaining(@Param("search") String search, PageRequest pageRequest);

    //MySQL FULLTEXT (ngram)
    @Query(value = "SELECT * FROM place p WHERE MATCH(p.name, p.address) AGAINST (:phrase IN BOOLEAN MODE)",
            countQuery = "SELECT COUNT(*) FROM place p WHERE MATCH(p.name, p.address) AGAINST (:phrase IN BOOLEAN MODE)",
            nativeQuery = true)
    Page<Place> searchFulltext(@Param("phrase") String phrase, Pageable pageable);
}
