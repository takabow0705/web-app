package project.calculator.domain.batch.porfolio.impl;

import io.grpc.finance.calculation.batch.portfolio.PortfolioEvaluationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.calculator.domain.batch.porfolio.PortfolioEvaluationService;
import project.calculator.domain.batch.porfolio.UnitPriceCalculator;
import project.calculator.domain.calendar.BusinessDays;
import project.calculator.domain.calendar.CalendarService;
import project.calculator.domain.calendar.CountryCode;
import project.infra.rdb.stockexecution.StockExecutionRepository;
import project.infra.rdb.stockexecution.entity.StockExecution;
import project.infra.rdb.stockportfolioevaluation.StockPortfolioEvaluation;
import project.infra.rdb.stockportfolioevaluation.StockPortfolioEvaluationBase;
import project.infra.rdb.stockportfolioevaluation.StockPortfolioEvaluationRepository;
import project.infra.rdb.strockpricetimeseries.StockPrice;
import project.infra.rdb.strockpricetimeseries.StockPriceBase;
import project.infra.rdb.strockpricetimeseries.StockPriceRepository;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortfolioEvaluationServiceImpl implements PortfolioEvaluationService {

    private final CalendarService calendarService;
    private final StockExecutionRepository stockExecutionRepository;
    private final StockPriceRepository stockPriceRepository;
    private final StockPortfolioEvaluationRepository stockPortfolioEvaluationRepository;

    private static final Logger logger = LoggerFactory.getLogger(PortfolioEvaluationServiceImpl.class);

    public PortfolioEvaluationServiceImpl(CalendarService calendarService, StockExecutionRepository stockExecutionRepository, StockPriceRepository stockPriceRepository, StockPortfolioEvaluationRepository stockPortfolioEvaluationRepository) {
        this.calendarService = calendarService;
        this.stockExecutionRepository = stockExecutionRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.stockPortfolioEvaluationRepository = stockPortfolioEvaluationRepository;
    }

    @Override
    @Async("asyncJobExecutor")
    @Transactional(rollbackOn = Exception.class)
    public void executeRegularEvaluation(PortfolioEvaluationRequest request) {
        logger.info(String.format("Start Regular Evaluation"));
        long start = System.currentTimeMillis();
        long stockPortfolioId = request.getPortfolioId();
        LocalDate startDate = LocalDate.parse(request.getStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate endDate = LocalDate.parse(request.getEndDate(), DateTimeFormatter.ISO_LOCAL_DATE);

        //評価済みの期間データを取得&評価対象の日付を取得
        BusinessDays businessDays = BusinessDays.of(this.calendarService.getBusinessDaysBetween(CountryCode.JP,startDate,endDate));

        BusinessDays notTargetDays = BusinessDays.of(this.stockPortfolioEvaluationRepository.findAll()
                        .stream()
                        .map(StockPortfolioEvaluationBase::getBaseDate)
                        .collect(Collectors.toSet()));

        BusinessDays targetDays = businessDays.removeAll(notTargetDays);

        //評価対象約定データを取得
        Specification<StockExecution> specification = StockExecution.executionDateBefore(endDate).and(StockExecution.equalsTo(stockPortfolioId));
        List<StockExecution> evaluationTarget = this.stockExecutionRepository.findAll(specification);

        if (evaluationTarget == null || evaluationTarget.isEmpty()){
            logger.warn(String.format("Execution Data is Empty. So Evaluation Job is not executed. Params: %s", request.toString()));
            return;
        }

        //評価に利用するマーケットデータを取得
        Set<String> stockCodes = evaluationTarget.stream().map(StockExecution::getStockCode).collect(Collectors.toSet());
        List<StockPrice> stockPrice = this.stockPriceRepository.retrieveByStockCode(stockCodes,startDate,endDate);

        //株式コードごとに分割し非同期で実行
        List<CompletableFuture<List<StockPortfolioEvaluation>>> futures = new ArrayList<>();
        Map<String, List<StockExecution>> executionByStockCode = evaluationTarget.stream().collect(Collectors.groupingBy(StockExecution::getStockCode));
        Map<String, List<StockPrice>> stockPriceByStockCode = stockPrice.stream().collect(Collectors.groupingBy(StockPriceBase::getStockCode));
        for (String stockCode : stockCodes){
            List<StockExecution> target = executionByStockCode.get(stockCode);
            List<StockPrice> price = stockPriceByStockCode.get(stockCode);
            UnitPriceCalculator<List<StockPortfolioEvaluation>> calculator = MovingAverageUnitPriceCalculator.generate(target, price, targetDays);
            CompletableFuture<List<StockPortfolioEvaluation>> future = CompletableFuture.supplyAsync(() -> calculator.calculate());
            futures.add(future);
        }
        // fork-join
        List<StockPortfolioEvaluation> evaluationResult = futures.stream().map(CompletableFuture::join).flatMap(sl -> sl.stream()).collect(Collectors.toList());
        //bulkInsert(全て新規作成なので全て正常に更新されるはず
        this.stockPortfolioEvaluationRepository.saveAll(evaluationResult);
        logger.info(String.format("Register [%s] Evaluation Results.", evaluationResult.size()));
        logger.info(String.format("Finish Evaluation. Processing Time : %d [ms]", System.currentTimeMillis() - start));
    }

    @Override
    @Async("asyncJobExecutor")
    @Transactional(rollbackOn = Exception.class)
    public void executeForceEvaluation(PortfolioEvaluationRequest request) {
        logger.info(String.format("Start Force Evaluation"));
        long start = System.currentTimeMillis();
        long stockPortfolioId = request.getPortfolioId();
        // 対象期間の評価履歴を削除
        LocalDate startDate = LocalDate.parse(request.getStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate endDate = LocalDate.parse(request.getEndDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        List<StockPortfolioEvaluation> deleteTarget = this.stockPortfolioEvaluationRepository
                .findAllByStockPortfolioIdEqualsAndBaseDateBeforeAndBaseDateAfter(stockPortfolioId, endDate, startDate);
        this.stockPortfolioEvaluationRepository.deleteAll(deleteTarget);

        logger.info(String.format("Delete [%s] evaluation result.", deleteTarget.size()));

        //評価済みの期間データを取得&評価対象の日付を取得
        BusinessDays targetDays = BusinessDays.of(this.calendarService.getBusinessDaysBetween(CountryCode.JP,startDate,endDate));

        //評価対象約定データを取得
        Specification<StockExecution> specification = StockExecution.executionDateBefore(endDate).and(StockExecution.equalsTo(stockPortfolioId));
        List<StockExecution> evaluationTarget = this.stockExecutionRepository.findAll(specification);

        if (evaluationTarget == null || evaluationTarget.isEmpty()){
            logger.warn(String.format("Execution Data is Empty. So Evaluation Job is not executed. Params: %s", request.toString()));
            return;
        }

        //評価に利用するマーケットデータを取得
        Set<String> stockCodes = evaluationTarget.stream().map(StockExecution::getStockCode).collect(Collectors.toSet());
        List<StockPrice> stockPrice = this.stockPriceRepository.retrieveByStockCode(stockCodes,startDate,endDate);

        //株式コードごとに分割し非同期で実行
        List<CompletableFuture<List<StockPortfolioEvaluation>>> futures = new ArrayList<>();
        Map<String, List<StockExecution>> executionByStockCode = evaluationTarget.stream().collect(Collectors.groupingBy(StockExecution::getStockCode));
        Map<String, List<StockPrice>> stockPriceByStockCode = stockPrice.stream().collect(Collectors.groupingBy(StockPriceBase::getStockCode));
        for (String stockCode : stockCodes){
            List<StockExecution> target = executionByStockCode.get(stockCode);
            List<StockPrice> price = stockPriceByStockCode.get(stockCode);
            UnitPriceCalculator<List<StockPortfolioEvaluation>> calculator = MovingAverageUnitPriceCalculator.generate(target, price, targetDays);
            CompletableFuture<List<StockPortfolioEvaluation>> future = CompletableFuture.supplyAsync(() -> calculator.calculate());
            futures.add(future);
        }
        // fork-join
        List<StockPortfolioEvaluation> evaluationResult = futures.stream().map(CompletableFuture::join).flatMap(sl -> sl.stream()).collect(Collectors.toList());
        //bulkInsert(全て新規作成なので全て正常に更新されるはず
        this.stockPortfolioEvaluationRepository.saveAll(evaluationResult);
        logger.info(String.format("Register [%s] Evaluation Results.", evaluationResult.size()));
        logger.info(String.format("Finish Evaluation. Processing Time : %d [ms]", System.currentTimeMillis() - start));
    }

    @Override
    @Async("asyncJobExecutor")
    @Transactional(rollbackOn = Exception.class)
    public void executeReviseEvaluation(PortfolioEvaluationRequest request) {
        logger.info(String.format("Start Revise Evaluation"));
        long start = System.currentTimeMillis();
        long stockPortfolioId = request.getPortfolioId();
        // 対象の評価履歴を取得
        List<StockPortfolioEvaluation> updateTarget = this.stockPortfolioEvaluationRepository
                .findAllByStockPortfolioIdEqualsAndLockOutTrueAndDeletedFalse(stockPortfolioId);
        Set<LocalDate> targetDates = updateTarget.stream().map(StockPortfolioEvaluationBase::getBaseDate).collect(Collectors.toSet());

        Map<Pair<String, LocalDate>, StockPrice> marketData = this.stockPriceRepository.findAllByBaseDateInAndDeletedFalse(targetDates).stream().collect(Collectors.toMap(s -> Pair.of(s.getStockCode(), s.getBaseDate()), Function.identity()));
        Map<Pair<String, LocalDate>, StockPortfolioEvaluation> target = updateTarget.stream().collect(Collectors.toMap(s -> Pair.of(s.getStockCode(), s.getBaseDate()), Function.identity()));

        List<StockPortfolioEvaluation> result = new ArrayList<>();
        for (Pair<String, LocalDate> dataKey : target.keySet()){
            StockPrice currentMarketData = marketData.get(dataKey);
            if (currentMarketData == null){
                logger.warn(String.format("Cannot get StockPrice. Datakey: [%s]", dataKey.toString()));
                continue;
            }

            BigDecimal currentPrice = currentMarketData.getClosePrice();
            StockPortfolioEvaluation evaluation = target.get(dataKey);

            BigDecimal currentPl = currentPrice.subtract(evaluation.getBookValue());
            currentPl.setScale(10, RoundingMode.DOWN);

            evaluation.setCurrentValue(currentPrice);
            evaluation.setCurrentPl(currentPl);
            evaluation.setUpdateUser("Calculator_"+Thread.currentThread()+this.getClass().getName());
            evaluation.setUpdateTimestamp(Timestamp.valueOf(LocalDateTime.now()));
            evaluation.setLockOut(!dataKey.getSecond().equals(currentMarketData.getBaseDate()));
            evaluation.setEvaluationDateBaseDate(currentMarketData.getBaseDate());
            result.add(evaluation);
        }
        this.stockPortfolioEvaluationRepository.saveAll(result);
        logger.info(String.format("Register [%s] Evaluation Results.", updateTarget.size()));
        logger.info(String.format("Finish Evaluation. Processing Time : %d [ms]", System.currentTimeMillis() - start));
    }
}
