package app.component.service.products.bondMaster;

import app.commons.entities.products.BondMaster;

import java.util.List;

public interface BondMasterManagementService {

    abstract List<BondMaster> findAll();
}
