// Unpaids event extractor: Service Bus topic + subscription, identity, RBAC and the ACA scheduled Jobs.
// Assumes platform-owned resources already exist: a VNet-integrated Container Apps environment with
// a route to the on-prem Oracle DBs, a Premium Service Bus namespace, a Key Vault holding the DB
// passwords, an ACR, and Application Insights.
targetScope = 'resourceGroup'

@description('Short environment name, e.g. dev')
param env string

param location string = resourceGroup().location

@description('Resource id of the existing Container Apps environment')
param containerAppsEnvironmentId string

@description('Full image reference, e.g. myacr.azurecr.io/unpaids-event-extractor:1.0.0')
param image string

@description('ACR login server, e.g. myacr.azurecr.io (identity needs AcrPull, granted by platform)')
param acrServer string

param serviceBusNamespaceName string
param keyVaultName string

@secure()
param appInsightsConnectionString string

@description('JDBC URLs for the three existing monolith databases')
param mainDbUrl string
param plDbUrl string
param clDbUrl string

@description('Comma-separated manual unpaid transaction type codes')
param plManualTxnTypes string
param clManualTxnTypes string

// One ACA Job per schedule, same image. ACA evaluates cron in UTC only, so each window is widened to
// cover both GMT and BST (Europe/London). Overlapping executions are safe: a daily feed can only be claimed by one execution.
//   batch: every 10 min 09:00-11:50 UTC = London 10:00-12:50 (BST) / 09:00-11:50 (GMT); PL/CL jobs finish ~10:30
//   sweep: on the hour 15:00-18:00 UTC = covers London 16:00 and 18:00 in both BST and GMT
@description('Schedules (cron in UTC), one ACA Job each')
param schedules array = [
  {
    name: 'batch'
    cron: '*/10 9-11 * * 1-5'
  }
  {
    name: 'sweep'
    cron: '0 15-18 * * 1-5'
  }
]

@description('Dev only: create topic unpaids-events-sandbox for laptop testing (profile local-azure)')
param createSandboxTopic bool = false

@description('Dev only: Entra group object id of developers allowed to send/receive on the sandbox topic')
param developerGroupObjectId string = ''

@description('Subscriptions to create on the topic (one per consumer)')
param consumerSubscriptions array = [
  'flexys-integration'
]

var serviceBusDataSenderRoleId = '69a216fc-b8fb-44d8-bc22-1f3c2cd27a39'
var serviceBusDataReceiverRoleId = '4f6d3b9b-027b-4f4c-9142-0e5a2a2247e0'
var keyVaultSecretsUserRoleId = '4633458b-17de-408a-b874-0445c86b69e6'

// ------------------------------------------------------------------ identity
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'id-unpaids-extractor-${env}'
  location: location
}

// ------------------------------------------------------------------ Service Bus
resource sbNamespace 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' existing = {
  name: serviceBusNamespaceName
}

resource topic 'Microsoft.ServiceBus/namespaces/topics@2022-10-01-preview' = {
  parent: sbNamespace
  name: 'unpaids-events'
  properties: {
    requiresDuplicateDetection: true
    duplicateDetectionHistoryTimeWindow: 'P1D'   // re-sends after a crash are dropped
    defaultMessageTimeToLive: 'P14D'
    supportOrdering: true
    enableBatchedOperations: true
  }
}

resource subscriptions 'Microsoft.ServiceBus/namespaces/topics/subscriptions@2022-10-01-preview' = [for name in consumerSubscriptions: {
  parent: topic
  name: name
  properties: {
    requiresSession: true                        // SessionId = PL:<loan> / CL:<loan> => per-loan ordering
    maxDeliveryCount: 10
    lockDuration: 'PT1M'
    deadLetteringOnMessageExpiration: true
    defaultMessageTimeToLive: 'P14D'
  }
}]

resource senderRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(topic.id, identity.id, serviceBusDataSenderRoleId)
  scope: topic
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', serviceBusDataSenderRoleId)
  }
}

// ------------------------------------------------------------------ dev sandbox (laptop testing)
// Same shape as the real topic, but no consumer subscriptions: local test events never reach consumers.
resource sandboxTopic 'Microsoft.ServiceBus/namespaces/topics@2022-10-01-preview' = if (createSandboxTopic) {
  parent: sbNamespace
  name: 'unpaids-events-sandbox'
  properties: {
    requiresDuplicateDetection: true
    duplicateDetectionHistoryTimeWindow: 'PT1H'
    defaultMessageTimeToLive: 'P1D'
    supportOrdering: true
  }
}

resource sandboxInspect 'Microsoft.ServiceBus/namespaces/topics/subscriptions@2022-10-01-preview' = if (createSandboxTopic) {
  parent: sandboxTopic
  name: 'developer-inspect'
  properties: {
    requiresSession: true
    maxDeliveryCount: 10
    defaultMessageTimeToLive: 'P1D'
  }
}

resource sandboxDevSender 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (createSandboxTopic && !empty(developerGroupObjectId)) {
  name: guid(sbNamespace.id, 'unpaids-events-sandbox', developerGroupObjectId, serviceBusDataSenderRoleId)
  scope: sandboxTopic
  properties: {
    principalId: developerGroupObjectId
    principalType: 'Group'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', serviceBusDataSenderRoleId)
  }
}

resource sandboxDevReceiver 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (createSandboxTopic && !empty(developerGroupObjectId)) {
  name: guid(sbNamespace.id, 'unpaids-events-sandbox', developerGroupObjectId, serviceBusDataReceiverRoleId)
  scope: sandboxTopic
  properties: {
    principalId: developerGroupObjectId
    principalType: 'Group'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', serviceBusDataReceiverRoleId)
  }
}

// ------------------------------------------------------------------ Key Vault access
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

resource kvRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, identity.id, keyVaultSecretsUserRoleId)
  scope: keyVault
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', keyVaultSecretsUserRoleId)
  }
}

// ------------------------------------------------------------------ the job
resource jobs 'Microsoft.App/jobs@2024-03-01' = [for schedule in schedules: {
  name: 'job-unpaids-extractor-${schedule.name}-${env}'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identity.id}': {}
    }
  }
  properties: {
    environmentId: containerAppsEnvironmentId
    configuration: {
      triggerType: 'Schedule'
      scheduleTriggerConfig: {
        cronExpression: schedule.cron
        parallelism: 1
        replicaCompletionCount: 1
      }
      replicaTimeout: 1800                       // 30 min hard stop; claim-expires-after must be longer
      replicaRetryLimit: 0                       // the next tick is the retry; failures stay visible
      registries: [
        {
          server: acrServer
          identity: identity.id
        }
      ]
      secrets: [
        {
          name: 'main-db-password'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/unpaids-extractor-main-db-password'
          identity: identity.id
        }
        {
          name: 'pl-db-password'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/unpaids-extractor-pl-db-password'
          identity: identity.id
        }
        {
          name: 'cl-db-password'
          keyVaultUrl: '${keyVault.properties.vaultUri}secrets/unpaids-extractor-cl-db-password'
          identity: identity.id
        }
        {
          name: 'appinsights-connection-string'
          value: appInsightsConnectionString
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'extractor'
          image: image
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: env }
            { name: 'AZURE_CLIENT_ID', value: identity.properties.clientId }
            { name: 'SERVICEBUS_NAMESPACE', value: '${serviceBusNamespaceName}.servicebus.windows.net' }
            { name: 'MAIN_DB_URL', value: mainDbUrl }
            { name: 'MAIN_DB_PASSWORD', secretRef: 'main-db-password' }
            { name: 'PL_DB_URL', value: plDbUrl }
            { name: 'PL_DB_PASSWORD', secretRef: 'pl-db-password' }
            { name: 'CL_DB_URL', value: clDbUrl }
            { name: 'CL_DB_PASSWORD', secretRef: 'cl-db-password' }
            { name: 'PL_MANUAL_TXN_TYPES', value: plManualTxnTypes }
            { name: 'CL_MANUAL_TXN_TYPES', value: clManualTxnTypes }
            { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', secretRef: 'appinsights-connection-string' }
            { name: 'OTEL_SERVICE_NAME', value: 'unpaids-event-extractor' }
          ]
        }
      ]
    }
  }
  dependsOn: [
    kvRole
    senderRole
  ]
}]

output jobNames array = [for (schedule, i) in schedules: jobs[i].name]
output identityClientId string = identity.properties.clientId
output identityPrincipalId string = identity.properties.principalId
