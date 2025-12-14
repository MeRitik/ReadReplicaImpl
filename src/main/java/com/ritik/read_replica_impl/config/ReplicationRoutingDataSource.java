package com.ritik.read_replica_impl.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

            System.out.println("Transaction active | Read-only: " + isReadOnly);
            return isReadOnly ? DataSourceType.REPLICA : DataSourceType.PRIMARY;
        } else {
            System.out.println("Transaction inactive");
            return DataSourceType.PRIMARY;
        }
    }
}
