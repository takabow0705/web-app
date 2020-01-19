package app.component.service.products.bondMaster;

import app.commons.entities.products.BondMaster;
import app.component.repository.product.BondMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Qualifier("bondMasterManagementService")
public class BondMasterManagementServiceImpl implements BondMasterManagementService{
    /** 債権情報管理のレポジトリクラス*/
    @Autowired
    private BondMasterRepository bondMasterRepository;

    /**
     * <p>
     *     債券マスタのデータをすべて返却する。
     *     その際に削除フラグは無視される。
     * </p>
     * @return
     */
    public List<BondMaster> findAll(){
        return this.bondMasterRepository.findAll();
    }
}