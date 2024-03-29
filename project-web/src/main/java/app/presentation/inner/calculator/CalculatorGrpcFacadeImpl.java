package app.presentation.inner.calculator;

import app.presentation.inner.calculator.service.BondPricingGrpcClientService;
import org.springframework.stereotype.Component;

@Component
public class CalculatorGrpcFacadeImpl implements CalculatorGrpcFacade {

    private final BondPricingGrpcClientService bondPricingGrpcClientService;

    public CalculatorGrpcFacadeImpl(BondPricingGrpcClientService bondPricingGrpcClientService) {
        this.bondPricingGrpcClientService = bondPricingGrpcClientService;
    }

    public BondPricingGrpcClientService getBondPricingGrpcClientService() {
        return this.bondPricingGrpcClientService;
    }
}
