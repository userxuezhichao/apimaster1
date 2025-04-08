package io.metersphere.plugin.api.spi;


import io.metersphere.plugin.api.dto.ParameterConfig;
import org.apache.jorphan.collections.HashTree;
import org.pf4j.ExtensionPoint;

/**
 * @author jianxing
 * @createTime 2021-10-30  10:07
 * 将 MsTestElement 具体实现类转换为 HashTree
 */
public interface JmeterElementConverter<T extends MsTestElement> extends ExtensionPoint {

    /**
     * 将 MsTestElement 具体实现类转换为 HashTree
     */
    void toHashTree(HashTree tree, T element, ParameterConfig config);
}
