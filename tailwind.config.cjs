/** Include classes from server fragments and dynamically generated controls. */
module.exports = {
  content: [
    './src/main/resources/templates/**/*.html',
    './src/main/resources/static/js/**/*.js',
    './src/main/java/net/olaba/mvnbuilder/controller/**/*.java',
  ],
  theme: { extend: {} },
  plugins: [],
};
