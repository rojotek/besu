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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
  private static final Address SENDER_ADDRESS =
      Address.fromHexString("0x2000000000000000000000000000000000000000");
  private static final Address NEW_ACCOUNT_ADDRESS =
      Address.fromHexString("0x3000000000000000000000000000000000000000");
  // ECRECOVER precompile address
  private static final Address PRECOMPILE_ADDRESS = Address.ECREC;
  private static final Wei TRANSFER_AMOUNT = Wei.of(100);
  private static final Wei ZERO_AMOUNT = Wei.ZERO;

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
    // For sender address
    lenient().when(messageFrame.getRecipientAddress()).thenReturn(SENDER_ADDRESS);
    // Default to non-static context
    lenient().when(messageFrame.isStatic()).thenReturn(false);
  }

  @Test
  void shouldTransferValueWithWarmAddress() {
    // Configure frame with sufficient gas and stack items
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            ACCOUNT_ADDRESS);
    when(messageFrame.stackSize()).thenReturn(2);
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
    when(messageFrame.stackSize()).thenReturn(2);
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
  void shouldTransferZeroValue() {
    // Configure frame with zero value transfer amount
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(messageFrame.popStackItem()).thenReturn(Bytes32.ZERO, ACCOUNT_ADDRESS);
    when(messageFrame.stackSize()).thenReturn(2);
    when(messageFrame.warmUpAddress(RECIPIENT)).thenReturn(true);
    when(senderAccount.getBalance()).thenReturn(Wei.of(1000));

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify successful zero-value transfer
    verify(messageFrame).decrementRemainingGas(gasCalculator.getWarmStorageReadCost());
    verify(senderAccount).decrementBalance(ZERO_AMOUNT);
    verify(recipientAccount).incrementBalance(ZERO_AMOUNT);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getWarmStorageReadCost());
  }

  @Test
  void shouldFailForInvalidAddressWithHighBytesSet() {
    // Configure frame with an invalid address (high bytes set)
    when(messageFrame.stackSize()).thenReturn(2);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            INVALID_ACCOUNT_WITH_HIGH_BYTES);

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
    when(messageFrame.stackSize()).thenReturn(2);
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
    when(messageFrame.stackSize()).thenReturn(2);
    when(messageFrame.warmUpAddress(RECIPIENT)).thenReturn(true);
    when(senderAccount.getBalance()).thenReturn(Wei.of(10)); // Not enough balance

    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify operation failed due to insufficient balance
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
  }

  @Test
  void shouldFailInStaticCall() {
    // Configure frame as static call
    when(messageFrame.isStatic()).thenReturn(true);
    
    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify operation failed due to static call
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    verify(messageFrame, never()).popStackItem();
  }

  @Test
  void shouldFailWithInsufficientStackItems() {
    // Configure stack with not enough items
    when(messageFrame.stackSize()).thenReturn(1); // Only one item on stack
    
    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);

    // Verify operation failed due to insufficient stack items
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
  }
  
  @Test
  void shouldHandleSelfTransfer() {
    // Create self-transfer scenario where sender sends to its own address
    final Bytes32 SENDER_BYTES = 
        Bytes32.fromHexString("0x0000000000000000000000002000000000000000000000000000000000000000");
    final Wei INITIAL_BALANCE = Wei.of(1000);
    
    // Setup for self-transfer
    when(messageFrame.stackSize()).thenReturn(2);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            SENDER_BYTES);
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(senderAccount.getBalance()).thenReturn(INITIAL_BALANCE);
    
    // We need to mock that worldUpdater.getOrCreate returns the same sender account
    when(worldUpdater.getOrCreate(SENDER_ADDRESS)).thenReturn(senderAccount);
    when(messageFrame.warmUpAddress(SENDER_ADDRESS)).thenReturn(true);
    
    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);
    
    // Verify operation succeeded
    assertThat(result.getHaltReason()).isNull();
    
    // Verify gas was charged
    verify(messageFrame).decrementRemainingGas(gasCalculator.getWarmStorageReadCost());
    
    // Verify that balance was "transferred" (removed and then added back)
    verify(senderAccount).decrementBalance(TRANSFER_AMOUNT);
    verify(senderAccount).incrementBalance(TRANSFER_AMOUNT);
  }
  
  @Test
  void shouldCreateNewAccountOnTransfer() {
    // Setup for new account creation
    final Bytes32 NEW_ACCOUNT_BYTES = 
        Bytes32.fromHexString("0x0000000000000000000000003000000000000000000000000000000000000000");
    final Wei INITIAL_BALANCE = Wei.of(1000);
    final MutableAccount newAccount = mock(MutableAccount.class);
    
    when(messageFrame.stackSize()).thenReturn(2);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            NEW_ACCOUNT_BYTES);
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(senderAccount.getBalance()).thenReturn(INITIAL_BALANCE);
    
    // Mock worldUpdater to return our new account mock when getOrCreate is called
    when(worldUpdater.getOrCreate(NEW_ACCOUNT_ADDRESS)).thenReturn(newAccount);
    when(messageFrame.warmUpAddress(NEW_ACCOUNT_ADDRESS)).thenReturn(false); // Cold account access
    
    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);
    
    // Verify operation succeeded
    assertThat(result.getHaltReason()).isNull();
    
    // Verify gas cost for cold account access was charged
    verify(messageFrame).decrementRemainingGas(gasCalculator.getColdAccountAccessCost());
    
    // Verify that balance was transferred from sender to the new account
    verify(senderAccount).decrementBalance(TRANSFER_AMOUNT);
    verify(newAccount).incrementBalance(TRANSFER_AMOUNT);
  }
  
  @Test
  void shouldTransferToPrecompileAddress() {
    // Create test for transferring to a precompile address (ECRECOVER = 0x01)
    final Bytes32 PRECOMPILE_BYTES = 
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    final Wei INITIAL_BALANCE = Wei.of(1000);
    final MutableAccount precompileAccount = mock(MutableAccount.class);
    
    when(messageFrame.stackSize()).thenReturn(2);
    when(messageFrame.popStackItem())
        .thenReturn(
            Bytes32.leftPad(Bytes.ofUnsignedLong(TRANSFER_AMOUNT.getAsBigInteger().longValue())),
            PRECOMPILE_BYTES);
    when(messageFrame.getRemainingGas()).thenReturn(10000L);
    when(senderAccount.getBalance()).thenReturn(INITIAL_BALANCE);
    
    // Mock isPrecompile in the gas calculator (used in warmUpAddress)
    when(messageFrame.warmUpAddress(PRECOMPILE_ADDRESS)).thenReturn(true); // Precompiles are warm
    when(worldUpdater.getOrCreate(PRECOMPILE_ADDRESS)).thenReturn(precompileAccount);
    
    // Execute operation
    OperationResult result = operation.execute(messageFrame, null);
    
    // Verify operation succeeded
    assertThat(result.getHaltReason()).isNull();
    
    // Verify warm access gas was charged (precompiles are always warm)
    verify(messageFrame).decrementRemainingGas(gasCalculator.getWarmStorageReadCost());
    
    // Verify that balance was transferred from sender to the precompile account
    verify(senderAccount).decrementBalance(TRANSFER_AMOUNT);
    verify(precompileAccount).incrementBalance(TRANSFER_AMOUNT);
  }
}