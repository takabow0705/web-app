package app.usecase.products.currencyMaster;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import project.infra.rdb.currencymaster.CurrencyMaster;
import project.infra.rdb.currencymaster.CurrencyMasterRepository;

import java.util.Collections;
import java.util.List;

@Service
@Qualifier("currencyMasterManagementService")
public class CurrencyMasterManagementServiceImpl implements CurrencyMasterManagementService{

    @Autowired
    private CurrencyMasterRepository currencyMasterRepository;

    @Override
    public List<CurrencyMaster> findAll() {
        List<CurrencyMaster> result = this.currencyMasterRepository.findAll();
        return result == null ? Collections.emptyList() : result;
    }
}
