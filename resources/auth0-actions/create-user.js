const axios = require("axios");


/**
 * Handler that will be called during the execution of a PostUserRegistration flow.
 *
 * @param {Event} event - Details about the context and user that has registered.
 */
exports.onExecutePostUserRegistration = async (event) => {
  const apiSecret = event.secrets.API_TOKEN;
  const apiDomain = event.secrets.API_DOMAIN;
  const response = await axios.post(`https://${apiDomain}/auth-zero/create-account`, {
    authZeroId: `${event.user.user_id}`,
    email: event.user.email
  }, {
    headers: {authorization: apiSecret}
  });

  console.log(`New ajanottaja user created ${response.data.id}`);

  return;
};
