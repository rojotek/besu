package org.hyperledger.besu.evm.operation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContracts;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;

public class AuthOperationTest {

  @Mock private MessageFrame messageFrame;
  @Mock private EVM evm;
  @Mock private PrecompiledContracts.PrecompileContract authPrecompile;
  @Mock private PrecompileContractRegistry precompileContractRegistry;
  @Mock private GasCalculator gasCalculator;
  @Mock private MetricsSystem metricsSystem;
  @Mock private OperationTimer operationTimer;

  private AuthOperation authOperation;

  @Before
  public void setup() {
    messageFrame = mock(MessageFrame.class);
    evm = mock(EVM.class);
    authPrecompile = mock(PrecompiledContracts.PrecompileContract.class);
    precompileContractRegistry = mock(PrecompileContractRegistry.class);
    gasCalculator = mock(GasCalculator.class);
    metricsSystem = mock(MetricsSystem.class);
    operationTimer = mock(OperationTimer.class);

    when(metricsSystem.createTimer("EVM", "AUTH")).thenReturn(operationTimer);
    when(precompileContractRegistry.get(PrecompiledContracts.ECDSA_RECOVER)).thenReturn(authPrecompile);
    when(gasCalculator.getBaseTierGasCost()).thenReturn(Gas.of(21000));

    authOperation = new AuthOperation(gasCalculator, precompileContractRegistry, metricsSystem);
  }

  @Test
  public void shouldSuccessfullyAuthorize() {
    // Setup successful authorization scenario
    // Mock the necessary frame and precompile behaviors
    // Assume the signature and message are valid and the precompile returns a successful result
    when(messageFrame.getStackItem(0)).thenReturn(BytesValue.fromHexString("valid_signature"));
    when(messageFrame.getStackItem(1)).thenReturn(BytesValue.fromHexString("valid_message"));
    when(authPrecompile.compute(any(), any())).thenReturn(BytesValue.fromHexString("authorized_address"));

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert successful authorization
    // Check that the authorized address is set correctly in the message frame
    assertThat(messageFrame.getAuthorizedAddress()).isEqualTo(BytesValue.fromHexString("authorized_address"));
    assertThat(result.getHaltReason()).isEmpty();
    assertThat(result.getGasCost()).isEmpty();
  }

  @Test
  public void shouldFailAuthorizationOnInvalidSignature() {
    // Setup invalid signature scenario
    // Mock the necessary frame and precompile behaviors to simulate an invalid signature
    when(messageFrame.getStackItem(0)).thenReturn(BytesValue.fromHexString("invalid_signature"));
    when(messageFrame.getStackItem(1)).thenReturn(BytesValue.fromHexString("valid_message"));
    when(authPrecompile.compute(any(), any())).thenReturn(BytesValue.EMPTY);

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert failure due to invalid signature
    // Check that the message frame does not set an authorized address
    assertThat(messageFrame.getAuthorizedAddress()).isEqualTo(BytesValue.EMPTY);
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    assertThat(result.getGasCost()).contains(gasCalculator.getBaseTierGasCost());
  }

  @Test
  public void shouldExceptionallyHaltOnPrecompileNotDefined() {
    // Setup scenario where precompile is not defined
    when(precompileContractRegistry.get(PrecompiledContracts.ECDSA_RECOVER)).thenReturn(null);

    // Execute the AUTH operation
    OperationResult result = authOperation.execute(messageFrame, evm);

    // Assert exceptional halt
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.PRECOMPILE_NOT_DEFINED);
  }

  @Test
  public void shouldCorrectlyCalculateGasCost() {
    // Setup scenario for gas cost calculation
    // Mock the necessary frame behaviors
    when(messageFrame.getStackItem(0)).thenReturn(BytesValue.fromHexString("valid_signature"));
    when(messageFrame.getStackItem(1)).thenReturn(BytesValue.fromHexString("valid_message"));

    // Calculate gas cost
    Gas cost = authOperation.cost(messageFrame);

    // Assert correct gas cost calculation
    // Check that the gas cost matches the expected cost as per EIP-3074
    assertThat(cost).isEqualTo(Gas.of(21000)); // Assuming 21000 is the correct gas cost as per EIP-3074
  }
}
