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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Implementation of the PAY opcode per EIP-5920.
 *
 * <p>This operation transfers ether from the current account to a target account without invoking
 * any code.
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-5920">EIP-5920: PAY opcode</a>
 */
public class PayOperation extends AbstractOperation {

  /** PAY opcode (0xfc) */
  public static final int OPCODE = 0xfc;

  /**
   * Instantiates a new PAY operation.
   *
   * @param gasCalculator the gas calculator
   */
  public PayOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "PAY", 2, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final Bytes value = frame.popStackItem();
    final Bytes addressBytes = frame.popStackItem();

    // PAY requires 2 items on the stack
    if (frame.stackSize() + 2 < 2) {
      return new OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    }

    // Check address high bytes are zero
    // Per EIP-5920: any non-zero values in the high 12 bytes cause the EVM to halt
    final Bytes32 addressBytes32 = Bytes32.leftPad(addressBytes);
    final Bytes addressHighBytes = addressBytes32.slice(0, 12);
    if (!addressHighBytes.isZero()) {
      return new OperationResult(0, ExceptionalHaltReason.INVALID_OPERATION);
    }

    // Extract the last 20 bytes for the address (Ethereum addresses are 20 bytes)
    final Bytes addressBytes20 = addressBytes32.slice(12, 20);
    final Address recipientAddress = Address.wrap(addressBytes20);
    final Wei transferAmount = Wei.wrap(value);

    final boolean accountIsWarm = frame.warmUpAddress(recipientAddress);
    final long warmAccessCost =
        accountIsWarm
            ? gasCalculator().getWarmStorageReadCost()
            : gasCalculator().getColdAccountAccessCost();

    // Calculate total gas cost
    final long cost = warmAccessCost;

    // Check for sufficient gas
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
    }

    // Get the current contract account (sender)
    MutableAccount contractAccount = frame.getWorldUpdater().getSenderAccount(frame);

    // Check for insufficient balance
    if (contractAccount.getBalance().compareTo(transferAmount) < 0) {
      return new OperationResult(cost, ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    // Charge gas
    frame.decrementRemainingGas(cost);

    // Get or create the recipient account and perform the transfer
    MutableAccount recipientAccount = frame.getWorldUpdater().getOrCreate(recipientAddress);
    contractAccount.decrementBalance(transferAmount);
    recipientAccount.incrementBalance(transferAmount);

    return new OperationResult(cost, null);
  }
}
