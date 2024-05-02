package org.hyperledger.besu.evm.operation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContract;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.datatypes.Bytes;
import org.hyperledger.besu.datatypes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Optional;

public class AuthOperationTest {

  @Mock private MessageFrame messageFrame;
  @Mock private EVM evm;
  @Mock private PrecompileContract authPrecompile;
  @Mock private PrecompileContractRegistry precompileContractRegistry;
  @Mock private GasCalculator gasCalculator;
  @Mock private MetricsSystem metricsSystem;
  @Mock private OperationTimer operationTimer;

  private AuthOperation authOperation;

  @BeforeEach
  public void setup() {
    messageFrame = mock(MessageFrame.class);
    evm = mock(EVM.class);
    authPrecompile = mock(PrecompileContract.class);
    precompileContractRegistry = mock(PrecompileContractRegistry.class);
    gasCalculator = mock(GasCalculator.class);
    metricsSystem = mock(MetricsSystem.class);
    operationTimer = mock(OperationTimer.class);

    when(metricsSystem.createTimer("EVM", "AUTH")).thenReturn(operationTimer);
    when(precompileContractRegistry.get(any())).thenReturn(Optional.of(authPrecompile));
    when(gasCalculator.getBaseTierGasCost()).thenReturn(Gas.of(21000));

    authOperation = new AuthOperation(gasCalculator, precompileContractRegistry, metricsSystem);
  }

  @Test
  public void shouldSuccessfullyAuthorize() {
    // Setup successful authorization scenario
    // Mock the necessary frame and precompile behaviors
    // Assume the signature and message are valid and the precompile returns a successful result
    Bytes signature = Bytes.of(1, 2, 3); // Example signature
    Bytes message = Bytes.of(1, 2, 3); // Example message
    Address authorizedAddress = Address.fromHexString("0xauthorized");

    when(messageFrame.getInputData()).thenReturn(Bytes.concatenate(message, signature));
    when(authPrecompile.compute(any(), any(), any())).thenReturn(authorizedAddress);

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert successful authorization
    // Check that the authorized address is set correctly in the message frame
    assertThat(messageFrame.getAuthorizedAddress()).isEqualTo(authorizedAddress);
    assertThat(result.getHaltReason()).isEmpty();
    // Assert correct gas cost for successful authorization as per EIP-3074
    assertThat(result.getGasCost()).contains(Gas.of(3000));
  }

  @Test
  public void shouldFailAuthorizationOnInvalidSignature() {
    // Setup invalid signature scenario
    // Mock the necessary frame and precompile behaviors to simulate an invalid signature
    Bytes signature = Bytes.of(1, 2, 3); // Example invalid signature
    Bytes message = Bytes.of(1, 2, 3); // Example message

    when(messageFrame.getInputData()).thenReturn(Bytes.concatenate(message, signature));
    when(authPrecompile.compute(any(), any(), any())).thenReturn(Bytes.EMPTY);

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert failure due to invalid signature
    // Check that the message frame does not set an authorized address
    assertThat(messageFrame.getAuthorizedAddress()).isNull();
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    assertThat(result.getGasCost()).contains(gasCalculator.getBaseTierGasCost());
  }

  @Test
  public void shouldExceptionallyHaltOnPrecompileNotDefined() {
    // Setup scenario where precompile is not defined
    when(precompileContractRegistry.get(any())).thenReturn(Optional.empty());

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert exceptional halt
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.PRECOMPILE_NOT_DEFINED);
  }

  @Test
  public void shouldCorrectlyCalculateGasCost() {
    // Setup scenario for gas cost calculation
    // Mock the necessary frame behaviors
    Bytes signature = Bytes.of(1, 2, 3); // Example signature
    Bytes message = Bytes.of(1, 2, 3); // Example message

    when(messageFrame.getInputData()).thenReturn(Bytes.concatenate(message, signature));

    // Calculate gas cost
    Gas cost = authOperation.cost(messageFrame);

    // Assert correct gas cost calculation as per EIP-3074
    assertThat(cost).isEqualTo(Gas.of(3000));
  }
}
