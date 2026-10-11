using 'main.bicep'

// TODO: replace every placeholder with the real dev values.
param env = 'dev'
param containerAppsEnvironmentId = '/subscriptions/<sub-id>/resourceGroups/<rg>/providers/Microsoft.App/managedEnvironments/<cae-name>'
param image = '<acr-name>.azurecr.io/unpaids-event-extractor:0.1.0'
param acrServer = '<acr-name>.azurecr.io'
param serviceBusNamespaceName = 'sb-premiumfinance-dev'
param keyVaultName = 'kv-premiumfinance-dev'
param appInsightsConnectionString = readEnvironmentVariable('APPINSIGHTS_CONNECTION_STRING', '')
param mainDbUrl = 'jdbc:oracle:thin:@//<main-db-host>:1521/<SERVICE>'
param plDbUrl = 'jdbc:oracle:thin:@//<pl-host>:1521/<SERVICE>'
param clDbUrl = 'jdbc:oracle:thin:@//<cl-host>:1521/<SERVICE>'
param plManualTxnTypes = '<PL manual unpaid txn type codes>'
param clManualTxnTypes = '<CL manual unpaid txn type codes>'
// Weekdays only, cron in UTC (see main.bicep). Change here if business hours change.
param schedules = [
  {
    name: 'batch'
    cron: '*/10 9-11 * * 1-5'
  }
  {
    name: 'sweep'
    cron: '0 15-18 * * 1-5'
  }
]
param createSandboxTopic = true
param developerGroupObjectId = '<entra-group-object-id-of-developers>'
param consumerSubscriptions = [
  'flexys-integration'
]
