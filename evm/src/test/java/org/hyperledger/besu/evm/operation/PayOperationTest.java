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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayOperationTest {

  private static final Bytes32 ACCOUNT_ADDRESS =
      Bytes32.fromHexString("0x0000000000000000000000001000000000000000000000000000000000000000");
  private static final Bytes32 INVALID_ACCOUNT_WITH_HIGH_BYTES =
      Bytes32.fromHexString("0x1000000000000000000000001000000000000000000000000000000000000000");
  private static final Address RECIPIENT =
      Address.fromHexString("0x1000000000000000000000000000000000000000");
  private static final Wei TRANSFER_AMOUNT = Wei.of(100);

  @Mock private MessageFrame messageFrame;
  @Mock private WorldUpdater worldUpdater;
  @Mock private MutableAccount senderAccount;
  @Mock private MutableAccount recipientAccount;

  private PayOperation operation;
  private final LondonGasCalculator gasCalculator = new LondonGasCalculator();

  @BeforeEach
  void setUp() {
    operation = new PayOperation(gasCalculator);

    // Use lenient() to avoid "unnecessary stubbing" errors
    lenient().when(messageFrame.getWorldUpdater()).thenReturn(worldUpdater);
    lenient().when(worldUpdater.getSenderAccount(messageFrame)).thenReturn(senderAccount);
    lenient().when(worldUpdater.getOrCreate(RECIPIENT)).thenReturn(recipientAccount);
  }

  @Test
  void shouldTransferValueWithWarmAddress() {
    // Configure frame with sufficient gas and stack items
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            ACCOUNT_ADDRESS);
    when(messageFrame.stackSize()).thenReturn(0);
    when(messageFrame.warmUpAddress(RECIPIENT)).thenReturn(true); // Address is already warm
    when(senderAccount.getBalance()).thenReturn(Wei.of(1000)); // Enough balance

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify successful transfer
    verify(messageFrame).decrementRemainingGas(gasCalculator.getWarmStorageReadCost());
    verify(senderAccount).decrementBalance(TRANSFER_AMOUNT);
    verify(recipientAccount).incrementBalance(TRANSFER_AMOUNT);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getWarmStorageReadCost());
  }

  @Test
  void shouldTransferValueWithColdAddress() {
    // Configure frame with sufficient gas and stack items
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            ACCOUNT_ADDRESS);
    when(messageFrame.stackSize()).thenReturn(0);
    when(messageFrame.warmUpAddress(RECIPIENT)).thenReturn(false); // Address is cold
    when(senderAccount.getBalance()).thenReturn(Wei.of(1000)); // Enough balance

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify successful transfer with cold address cost
    verify(messageFrame).decrementRemainingGas(gasCalculator.getColdAccountAccessCost());
    verify(senderAccount).decrementBalance(TRANSFER_AMOUNT);
    verify(recipientAccount).incrementBalance(TRANSFER_AMOUNT);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getColdAccountAccessCost());
  }

  @Test
  void shouldFailForInvalidAddressWithHighBytesSet() {
    // Configure frame with an invalid address (high bytes set)
    lenient().when(messageFrame.getRemainingGas()).thenReturn(10000L);
    lenient()
        .when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            INVALID_ACCOUNT_WITH_HIGH_BYTES);
    lenient().when(messageFrame.stackSize()).thenReturn(0);

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify operation failed due to invalid address
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
  }

  @Test
  void shouldFailForInsufficientGas() {
    // Configure frame with insufficient gas
    when(messageFrame.getRemainingGas()).thenReturn(1L); // Not enough gas
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            ACCOUNT_ADDRESS);
    when(messageFrame.stackSize()).thenReturn(0);
    when(messageFrame.warmUpAddress(RECIPIENT)).thenReturn(true);

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify operation failed due to insufficient gas
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_GAS);
  }

  @Test
  void shouldFailForInsufficientBalance() {
    // Configure frame with insufficient balance
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            ACCOUNT_ADDRESS);
    when(messageFrame.stackSize()).thenReturn(0);
    when(messageFrame.warmUpAddress(RECIPIENT)).thenReturn(true);
    when(senderAccount.getBalance()).thenReturn(Wei.of(10)); // Not enough balance

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify operation failed due to insufficient balance
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
  }
}
