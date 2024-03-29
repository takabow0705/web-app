package app.usecase.portfolio;


import app.domain.portfolio.StockPortfolioReferenceDto;
import org.springframework.stereotype.Service;
import project.infra.rdb.stockexecution.StockExecutionRepository;
import project.infra.rdb.stockportfolio.StockPortfolioRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockPortfolioServiceImpl implements StockPortfolioService {

    private final StockPortfolioRepository stockPortfolioRepository;

    private final StockExecutionRepository stockExecutionRepository;

    public StockPortfolioServiceImpl(StockPortfolioRepository stockPortfolioRepository, StockExecutionRepository stockExecutionRepository) {
        this.stockPortfolioRepository = stockPortfolioRepository;
        this.stockExecutionRepository = stockExecutionRepository;
    }

    /**
     * @param userId
     * @return
     */
    public List<StockPortfolioReferenceDto> findByUserId(long userId) {
        return this.stockPortfolioRepository.findByUserIdAndIsDeletedFalse(userId).stream()
                .map(StockPortfolioReferenceDto::createFrom)
                .collect(Collectors.toList());
    }

    @Override
    public void save(StockPortfolioReferenceDto dto) {
        //this.stockPortfolioRepository.save();
    }

    @Override
    public void update(StockPortfolioReferenceDto dto) {

    }
}
