package kr.hhplus.be.server.concert.repository;


import kr.hhplus.be.server.concert.domain.Concert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


@Repository
public class ConcertRepositoryImpl implements ConcertRepository {

    private final ConcertJpaRepository jpaRepository;

    public ConcertRepositoryImpl(ConcertJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Page<Concert> findAvailableConcerts(Pageable pageable) {
        return jpaRepository.findAvailableConcerts(LocalDate.now(), pageable);
    }

    @Override
    public Optional<Concert> findById(Long concertId) {
        return jpaRepository.findById(concertId);
    }

    @Override
    public List<Concert> findByConcertDate(LocalDate date) {
        return jpaRepository.findByConcertDate(date);
    }

    @Override
    public List<Concert> findByArtistContaining(String artist) {
        return jpaRepository.findByArtistContaining(artist);
    }

    @Override
    public List<Concert> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Concert save(Concert concert) {
        return jpaRepository.save(concert);
    }

    @Override
    public List<Concert> findBySoldOutTrue() {
        return jpaRepository.findBySoldOutTrue();
    }

    @Override
    public List<Concert> findAvailableConcertsForBatch() {
        return jpaRepository.findAvailableConcertsForBatch();
    }
}