package app.component.service.products.currencyMaster;


import project.infra.rdb.currencymaster.CurrencyMaster;

import java.util.List;

public interface CurrencyMasterManagementService {

    abstract List<CurrencyMaster> findAll();
}
