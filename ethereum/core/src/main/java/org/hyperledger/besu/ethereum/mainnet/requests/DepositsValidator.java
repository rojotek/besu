/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet.requests;

import static org.hyperledger.besu.ethereum.mainnet.requests.RequestUtil.getDepositRequests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.DepositDecoder;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositsValidator implements RequestValidator {

  private static final Logger LOG = LoggerFactory.getLogger(DepositsValidator.class);
  private final Address depositContractAddress;

  public DepositsValidator(final Address depositContractAddress) {
    this.depositContractAddress = depositContractAddress;
  }

  @Override
  public boolean validateParameter(final Optional<List<Request>> deposits) {
    return deposits.isPresent();
  }

  public boolean validateDeposits(
      final Block block,
      final List<Deposit> actualDeposits,
      final List<TransactionReceipt> receipts) {

    List<Deposit> expectedDeposits = new ArrayList<>();

    for (TransactionReceipt receipt : receipts) {
      for (Log log : receipt.getLogsList()) {
        if (depositContractAddress.equals(log.getLogger())) {
          Deposit deposit = DepositDecoder.decodeFromLog(log);
          expectedDeposits.add(deposit);
        }
      }
    }

    boolean isValid = actualDeposits.equals(expectedDeposits);

    if (!isValid) {
      LOG.warn(
          "Deposits validation failed. Deposits from block body do not match deposits from logs. Block hash: {}",
          block.getHash());
      LOG.debug(
          "Deposits from logs: {}, deposits from block body: {}", expectedDeposits, actualDeposits);
    }

    return isValid;
  }

  @Override
  public boolean validate(
      final Block block, final List<Request> requests, final List<TransactionReceipt> receipts) {
    var deposits = getDepositRequests(Optional.of(requests)).orElse(Collections.emptyList());
    return validateDeposits(block, deposits, receipts);
  }
}
