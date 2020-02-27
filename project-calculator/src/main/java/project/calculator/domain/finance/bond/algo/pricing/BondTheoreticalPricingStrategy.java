package project.calculator.domain.finance.bond.algo.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import project.calculator.data.BondData;
import project.calculator.domain.finance.bond.algo.CalculationStrategy;
import project.calculator.domain.repository.master.discountFactor.DiscountFactorDataRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Qualifier("useDF")
public class BondTheoreticalPricingStrategy implements CalculationStrategy<BondData> {

    private final DiscountFactorDataRepository discountFactorDataRepository;
    private static final Logger logger = LoggerFactory.getLogger(BondTheoreticalPricingStrategy.class);

    public BondTheoreticalPricingStrategy(final DiscountFactorDataRepository discountFactorDataRepository) {
        this.discountFactorDataRepository = discountFactorDataRepository;
    }

    @Override
    public BigDecimal execute(BondData data) {
        List<BigDecimal> discountFactor = this.handleDiscountFactor(data).stream()
                .limit(data.getCurrentMaturity().divide(data.getPaymentType().getStep()).longValue() + 1)
                .collect(Collectors.toList());

        if(isDiscountFactorNotEnough(data, discountFactor)){
            logger.info("Discount Factor data is not sufficient.");
            return BigDecimal.ZERO;
        }

        BigDecimal NPVOfPrincipal = discountFactor.get(discountFactor.size() - 1).multiply(data.getUnit());

        if (data.getIsTermEndPayment()) {
            BigDecimal NPVOfCoupon = discountFactor.stream().skip(1)
                    .map(d -> d.multiply(data.getCouponRate()))
                    .map(d -> d.multiply(data.getPaymentType().getStep()))
                    .limit(data.getCurrentMaturity().divide(data.getPaymentType().getStep()).longValue())
                    .reduce(BigDecimal.ZERO, (p, q) -> p.add(q));

            BigDecimal theoreticalPrice = NPVOfPrincipal.add(NPVOfCoupon.multiply(data.getUnit()));
            return theoreticalPrice;
        }

        BigDecimal NPVOfCoupon = discountFactor.stream()
                .map(d -> d.multiply(data.getCouponRate()))
                .map(d -> d.multiply(data.getPaymentType().getStep()))
                .limit(data.getCurrentMaturity().longValue())
                .reduce(BigDecimal.ZERO, (p, q) -> p.add(q));

        BigDecimal theoreticalPrice = NPVOfPrincipal.add(NPVOfCoupon.multiply(data.getUnit()));
        return theoreticalPrice;
    }

    /**
     * <p>
     * 利払い周期によって使用する割引現在価値を切り替える。
     * </p>
     *
     * @param bondData
     * @return 割引現在価値のMap
     */
    private List<BigDecimal> handleDiscountFactor(BondData bondData) {
        switch (bondData.getPaymentType()) {
            case Annual:
                return this.discountFactorDataRepository.loadAnnualDiscountFactor();
            case SemiAnnual:
                return this.discountFactorDataRepository.loadSemiAnnualDiscountFactor();
            default:
                return Collections.emptyList();
        }
    }

    /**
     * <p>
     * 計算に必要な割引現在価値が存在するか確認
     * </p>
     *
     * @param data
     * @return
     */
    private Boolean isDiscountFactorNotEnough(BondData data, List<BigDecimal> discountFactors){
        // 残存期間 * （半期 or 通期）で必要な割引現在価値の数を計算する。
        Integer termLength = data.getCurrentMaturity().intValue() * BigDecimal.ONE.divide(data.getPaymentType().getStep()).intValue();
        // 割引現在価値の数を求める。
        Integer discountFactorSize = discountFactors.size();
        return termLength > discountFactorSize;
    }
}
