/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.offer.Offer;
import io.bitsquare.trade.protocol.trade.taker.TakerAsSellerProtocol;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.Serializable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(TakerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum TakerLifeCycleState implements LifeCycleState {
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum TakerProcessState implements ProcessState {
        TAKE_OFFER_FEE_TX_CREATED,
        TAKE_OFFER_FEE_PUBLISHED,
        TAKE_OFFER_FEE_PUBLISH_FAILED,
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,
        FIAT_PAYMENT_STARTED,
        FIAT_PAYMENT_RECEIVED,
        PAYOUT_PUBLISHED,
        MESSAGE_SENDING_FAILED,
        UNSPECIFIC_FAULT;

        protected String errorMessage;

        @Override
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    protected TakerProcessState processState;
    protected TakerLifeCycleState lifeCycleState;

    transient protected ObjectProperty<TakerProcessState> processStateProperty = new SimpleObjectProperty<>(processState);
    transient protected ObjectProperty<TakerLifeCycleState> lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerTrade(Offer offer) {
        super(offer);
    }

    // Serialized object does not create our transient objects
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);
    }

    public void onFiatPaymentReceived() {
        ((TakerAsSellerProtocol) protocol).onFiatPaymentReceived();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setProcessState(TakerProcessState processState) {
        this.processState = processState;
        processStateProperty.set(processState);

        if (processState == TakerProcessState.UNSPECIFIC_FAULT) {
            setLifeCycleState(TakerLifeCycleState.FAILED);
            disposeProtocol();
        }
    }

    public void setLifeCycleState(TakerLifeCycleState lifeCycleState) {
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty.set(lifeCycleState);
    }

    public void setDepositTx(Transaction tx) {
        this.depositTx = tx;
        setConfidenceListener();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyObjectProperty<TakerProcessState> processStateProperty() {
        return processStateProperty;
    }

    public ReadOnlyObjectProperty<TakerLifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setConfidenceListener() {
        TransactionConfidence transactionConfidence = depositTx.getConfidence();
        ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
        Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
            @Override
            public void onSuccess(TransactionConfidence result) {
                if (processState.ordinal() < TakerProcessState.DEPOSIT_CONFIRMED.ordinal())
                    setProcessState(TakerProcessState.DEPOSIT_CONFIRMED);
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
                log.error(t.getMessage());
                Throwables.propagate(t);
            }
        });
    }
}