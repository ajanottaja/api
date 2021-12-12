const axios = require("axios");


/**
 * Handler that will be called during the execution of a PostLogin flow.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
exports.onExecutePostLogin = async (event, api) => {
  const apiSecret = event.secrets.API_TOKEN;
  const apiDomain = event.secrets.API_DOMAIN;
  const auth0Id = event.user.user_id;
  
  const response = await axios.get(`https://${apiDomain}/auth-zero/get-account/${auth0Id}`, {
    headers: {authorization: apiSecret}
  });

  console.log(response.data.id);
  api.accessToken.setCustomClaim("https://ajanottaja.app/sub", response.data.id)
  api.idToken.setCustomClaim("https://ajanottaja.app/sub", response.data.id)
};
